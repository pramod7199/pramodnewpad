#include <jni.h>

#include <oboe/Oboe.h>
#include <fluidsynth.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

class PianoEngine final : public oboe::AudioStreamDataCallback {
public:
    struct SampleVoice {
        int note = -1;
        int channel = 0;
        double position = 0.0;
        float gain = 0.0f;
        float release = 1.0f;
        bool releasing = false;
        bool active = false;
    };

    bool start() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (synth_) return true;
        pitchBend_.fill(8192);

        settings_ = new_fluid_settings();
        if (settings_ == nullptr) return false;
        fluid_settings_setnum(settings_, "synth.gain", 0.7);
        fluid_settings_setint(settings_, "synth.polyphony", 256);
        fluid_settings_setint(settings_, "synth.reverb.active", 1);
        fluid_settings_setint(settings_, "synth.chorus.active", 1);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Shared)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Stereo)
                ->setUsage(oboe::Usage::Game)
                ->setContentType(oboe::ContentType::Music)
                ->setDataCallback(this);
        const oboe::Result result = builder.openStream(stream_);
        if (result != oboe::Result::OK || !stream_) {
            delete_fluid_settings(settings_);
            settings_ = nullptr;
            return false;
        }

        fluid_settings_setnum(settings_, "synth.sample-rate", stream_->getSampleRate());
        synth_ = new_fluid_synth(settings_);
        if (synth_ == nullptr) {
            stream_->close();
            stream_.reset();
            delete_fluid_settings(settings_);
            settings_ = nullptr;
            return false;
        }

        if (stream_->requestStart() == oboe::Result::OK) return true;

        stream_->close();
        stream_.reset();
        delete_fluid_synth(synth_);
        delete_fluid_settings(settings_);
        synth_ = nullptr;
        settings_ = nullptr;
        return false;
    }

    void stop() {
        if (stream_) {
            stream_->requestStop();
            stream_->close();
            stream_.reset();
        }
        std::lock_guard<std::mutex> lock(mutex_);
        if (synth_) delete_fluid_synth(synth_);
        if (settings_) delete_fluid_settings(settings_);
        synth_ = nullptr;
        settings_ = nullptr;
    }

    bool loadSoundFont(const std::string& path) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!synth_) return false;
        const int id = fluid_synth_sfload(synth_, path.c_str(), 1);
        if (id < 0) return false;
        soundFonts_.push_back(id);
        const bool selected = fluid_synth_program_select(synth_, 0, id, 0, 0) == FLUID_OK;
        if (selected) sampleMode_ = false;
        return selected;
    }

    bool selectPreset(int bank, int preset) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!synth_ || soundFonts_.empty()) return false;
        const bool selected = fluid_synth_program_select(
                synth_, 0, soundFonts_.back(), bank, preset) == FLUID_OK;
        if (selected) sampleMode_ = false;
        return selected;
    }

    bool setSample(std::vector<float>&& pcm, int sampleRate, int channels) {
        if (pcm.empty() || sampleRate <= 0 || (channels != 1 && channels != 2)
                || pcm.size() % static_cast<size_t>(channels) != 0) {
            return false;
        }
        std::vector<float> previousSample;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            previousSample.swap(samplePcm_);
            samplePcm_ = std::move(pcm);
            sampleRate_ = sampleRate;
            sampleChannels_ = channels;
            if (synth_) fluid_synth_all_sounds_off(synth_, 0);
            for (auto& voice : sampleVoices_) voice = SampleVoice{};
            sampleMode_ = true;
        }
        return true;
    }

    void noteOn(int channel, int note, int velocity) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (sampleMode_) {
            if (samplePcm_.empty()) return;
            auto voice = std::find_if(sampleVoices_.begin(), sampleVoices_.end(),
                                      [](const SampleVoice& item) { return !item.active; });
            if (voice == sampleVoices_.end()) {
                voice = std::min_element(sampleVoices_.begin(), sampleVoices_.end(),
                        [](const SampleVoice& left, const SampleVoice& right) {
                            return left.position > right.position;
                        });
            }
            *voice = SampleVoice{};
            voice->note = note;
            voice->channel = std::clamp(channel, 0, static_cast<int>(pitchBend_.size()) - 1);
            voice->gain = std::clamp(velocity / 127.0f, 0.0f, 1.0f) * 0.8f;
            voice->active = true;
        } else if (synth_) {
            fluid_synth_noteon(synth_, channel, note, velocity);
        }
    }

    void noteOff(int channel, int note) {
        std::lock_guard<std::mutex> lock(mutex_);
        channel = std::clamp(channel, 0, static_cast<int>(pitchBend_.size()) - 1);
        if (sampleMode_) {
            for (auto& voice : sampleVoices_) {
                if (voice.active && voice.channel == channel && voice.note == note) {
                    voice.releasing = true;
                }
            }
        } else if (synth_) {
            fluid_synth_noteoff(synth_, channel, note);
        }
    }

    void pitchBend(int channel, int value) {
        std::lock_guard<std::mutex> lock(mutex_);
        channel = std::clamp(channel, 0, static_cast<int>(pitchBend_.size()) - 1);
        value = std::clamp(value, 0, 16383);
        pitchBend_[static_cast<size_t>(channel)] = value;
        if (synth_) fluid_synth_pitch_bend(synth_, channel, value);
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream, void* audioData, int32_t frames) override {
        auto* output = static_cast<float*>(audioData);
        std::lock_guard<std::mutex> lock(mutex_);
        if (!synth_) {
            std::fill(output, output + frames * 2, 0.0f);
        } else {
            left_.resize(static_cast<size_t>(frames));
            right_.resize(static_cast<size_t>(frames));
            if (fluid_synth_write_float(synth_, frames, left_.data(), 0, 1,
                                        right_.data(), 0, 1) != FLUID_OK) {
                std::fill(output, output + frames * 2, 0.0f);
            } else {
                for (int32_t i = 0; i < frames; ++i) {
                    output[i * 2] = left_[static_cast<size_t>(i)];
                    output[i * 2 + 1] = right_[static_cast<size_t>(i)];
                }
            }
        }

        if (!sampleMode_ || samplePcm_.empty()) return oboe::DataCallbackResult::Continue;
        const double outputRate = stream->getSampleRate();
        const double releaseStep = 1.0 / (outputRate * 0.015);
        const size_t frameCount = samplePcm_.size() / static_cast<size_t>(sampleChannels_);
        for (auto& voice : sampleVoices_) {
            if (!voice.active) continue;
            const double bendSemitones =
                    (pitchBend_[static_cast<size_t>(voice.channel)] - 8192) * (2.0 / 8192.0);
            const double rate = (sampleRate_ / outputRate)
                    * std::pow(2.0, (voice.note - 60 + bendSemitones) / 12.0);
            for (int32_t i = 0; i < frames; ++i) {
                const size_t sourceFrame = static_cast<size_t>(voice.position);
                if (sourceFrame >= frameCount) {
                    voice.active = false;
                    break;
                }
                const size_t nextFrame = std::min(sourceFrame + 1, frameCount - 1);
                const float fraction = static_cast<float>(voice.position - sourceFrame);
                const size_t first = sourceFrame * static_cast<size_t>(sampleChannels_);
                const size_t second = nextFrame * static_cast<size_t>(sampleChannels_);
                const float sampleLeft = samplePcm_[first] * (1.0f - fraction)
                        + samplePcm_[second] * fraction;
                const float sampleRight = sampleChannels_ == 1 ? sampleLeft
                        : samplePcm_[first + 1] * (1.0f - fraction)
                        + samplePcm_[second + 1] * fraction;
                const float envelope = voice.gain * voice.release;
                output[i * 2] = std::clamp(output[i * 2] + sampleLeft * envelope, -1.0f, 1.0f);
                output[i * 2 + 1] = std::clamp(
                        output[i * 2 + 1] + sampleRight * envelope, -1.0f, 1.0f);
                voice.position += rate;
                if (voice.releasing) {
                    voice.release -= static_cast<float>(releaseStep);
                    if (voice.release <= 0.0f) {
                        voice.active = false;
                        break;
                    }
                }
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

private:
    std::mutex mutex_;
    fluid_settings_t* settings_ = nullptr;
    fluid_synth_t* synth_ = nullptr;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::vector<int> soundFonts_;
    std::vector<float> left_;
    std::vector<float> right_;
    std::vector<float> samplePcm_;
    std::array<SampleVoice, 64> sampleVoices_{};
    std::array<int, 16> pitchBend_{};
    int sampleRate_ = 0;
    int sampleChannels_ = 0;
    bool sampleMode_ = false;
};

std::mutex gEngineMutex;
std::unique_ptr<PianoEngine> gEngine;

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pramod_piano_MainActivity_nativeCreate(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    gEngine = std::make_unique<PianoEngine>();
    return gEngine->start() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pramod_piano_MainActivity_nativeDestroy(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->stop();
    gEngine.reset();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pramod_piano_MainActivity_nativeLoadSoundFont(
        JNIEnv* env, jobject, jstring path) {
    const std::string file = toString(env, path);
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gEngine && gEngine->loadSoundFont(file) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pramod_piano_MainActivity_nativeSetSample(
        JNIEnv* env, jobject, jfloatArray pcm, jint sampleRate, jint channels) {
    if (pcm == nullptr) return JNI_FALSE;
    const jsize length = env->GetArrayLength(pcm);
    if (length <= 0) return JNI_FALSE;
    std::vector<float> samples(static_cast<size_t>(length));
    env->GetFloatArrayRegion(pcm, 0, length, samples.data());
    if (env->ExceptionCheck()) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gEngine && gEngine->setSample(std::move(samples), sampleRate, channels)
            ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pramod_piano_MainActivity_nativeSelectPreset(
        JNIEnv*, jobject, jint bank, jint preset) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gEngine && gEngine->selectPreset(bank, preset) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pramod_piano_MainActivity_nativeNoteOn(
        JNIEnv*, jobject, jint channel, jint note, jint velocity) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->noteOn(channel, note, velocity);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pramod_piano_MainActivity_nativeNoteOff(
        JNIEnv*, jobject, jint channel, jint note) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->noteOff(channel, note);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pramod_piano_MainActivity_nativePitchBend(
        JNIEnv*, jobject, jint channel, jint value) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->pitchBend(channel, value);
}
