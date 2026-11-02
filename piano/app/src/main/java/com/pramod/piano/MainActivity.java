package com.pramod.piano;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements PianoKeyboardView.NoteListener {
    private static final int REQUEST_SOUNDFONT = 4101;
    private static final int[] CHORD_ROOTS = {60, 67, 57, 65, 62, 64, 59, 60};
    private static final String[] CHORD_NAMES = {
            "C MAJOR", "G MAJOR", "A MINOR", "F MAJOR",
            "D MINOR", "E MINOR", "B DIM", "C MAJOR 7"
    };
    private static final int[][] CHORD_INTERVALS = {
            {0, 4, 7}, {0, 4, 7}, {0, 3, 7}, {0, 4, 7},
            {0, 3, 7}, {0, 3, 7}, {0, 3, 6}, {0, 4, 7, 11}
    };
    private static final String INSTRUMENT_ASSET_DIRECTORY = "instruments";

    static {
        System.loadLibrary("piano_audio_engine");
    }

    private PianoKeyboardView keyboard;
    private PianoPitchBendView pitchBendView;
    private LockableHorizontalScrollView keyboardScroll;
    private Spinner presetSpinner;
    private TextView status;
    private TextView activeInstrument;
    private TextView octaveLabel;
    private TextView masterVolumeLabel;
    private final TextView[] eqLabels = new TextView[3];
    private final float[] eqValues = new float[3];
    private boolean engineReady;
    private boolean soundFontLoaded;
    private boolean presetReady;
    private int octaveShift;
    private int samplePresetCount;
    private int presetLoadGeneration;
    private String selectedSoundFont;
    private final List<PresetOption> presetOptions = new ArrayList<>();
    private final ExecutorService sampleDecoder = Executors.newSingleThreadExecutor();

    private native boolean nativeCreate();
    private native void nativeDestroy();
    private native boolean nativeLoadSoundFont(String path);
    private native boolean nativeSetSample(float[] pcm, int sampleRate, int channels);
    private native boolean nativeSelectPreset(int bank, int preset);
    private native void nativeNoteOn(int channel, int note, int velocity);
    private native void nativeNoteOff(int channel, int note);
    private native void nativePitchBend(int channel, int value);
    private native void nativeSetMasterVolume(float volume);
    private native void nativeSetEqualizer(float lowDb, float midDb, float highDb);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main);

        keyboard = findViewById(R.id.pianoKeyboard);
        keyboard.setNoteListener(this);
        status = findViewById(R.id.txtStatus);
        activeInstrument = findViewById(R.id.txtActiveInstrument);
        octaveLabel = findViewById(R.id.txtOctave);
        pitchBendView = findViewById(R.id.pitchBend);
        keyboardScroll = findViewById(R.id.keyboardScroll);
        presetSpinner = findViewById(R.id.spinnerPreset);
        pitchBendView.setBendListener(value -> {
            if (engineReady) nativePitchBend(0, value);
        });
        setupChordButtons();
        setupSoundFontPicker();
        setupOctaveAndScrollLock();
        setupAudioControls();

        engineReady = nativeCreate();
        if (!engineReady) {
            status.setText("Audio engine failed to start");
            Toast.makeText(this, "Could not start Oboe / FluidSynth", Toast.LENGTH_LONG).show();
        } else {
            nativeSetMasterVolume(0.8f);
            nativeSetEqualizer(0f, 0f, 0f);
            loadBundledInstrumentPresets();
        }
    }

    private void setupChordButtons() {
        LinearLayout chordRow = findViewById(R.id.chordRow);
        for (int chordIndex = 0; chordIndex < CHORD_NAMES.length; chordIndex++) {
            final int index = chordIndex;
            Button chord = new Button(this);
            chord.setText(CHORD_NAMES[chordIndex]);
            chord.setTextColor(0xFFEAF1FF);
            chord.setTextSize(10f);
            chord.setAllCaps(false);
            chord.setMinWidth(dp(60));
            chord.setPadding(dp(4), 0, dp(4), 0);
            chord.setBackgroundResource(R.drawable.chord_button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            params.setMargins(dp(3), dp(2), dp(3), dp(2));
            chordRow.addView(chord, params);
            chord.setOnTouchListener((view, event) -> {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    playChord(index, true);
                    return true;
                }
                if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) {
                    playChord(index, false);
                    return true;
                }
                return true;
            });
        }
    }

    private void setupOctaveAndScrollLock() {
        findViewById(R.id.btnOctaveDown).setOnClickListener(v -> changeOctave(-1));
        findViewById(R.id.btnOctaveUp).setOnClickListener(v -> changeOctave(1));
        Button lockButton = findViewById(R.id.btnScrollLock);
        lockButton.setOnClickListener(v -> {
            boolean locked = !keyboardScroll.isScrollLocked();
            keyboardScroll.setScrollLocked(locked);
            lockButton.setText(locked ? "SCROLL LOCKED" : "SCROLL UNLOCKED");
            status.setText(locked ? "Keyboard scroll locked" : "Keyboard scroll unlocked");
        });
    }

    private void setupAudioControls() {
        SeekBar volume = findViewById(R.id.seekMasterVolume);
        masterVolumeLabel = findViewById(R.id.txtMasterVolume);
        volume.setMax(100);
        volume.setProgress(80);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                masterVolumeLabel.setText(progress + "%");
                if (engineReady) nativeSetMasterVolume(progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        int[] seekIds = {R.id.seekEqLow, R.id.seekEqMid, R.id.seekEqHigh};
        int[] labelIds = {R.id.txtEqLow, R.id.txtEqMid, R.id.txtEqHigh};
        for (int band = 0; band < seekIds.length; band++) {
            final int index = band;
            SeekBar equalizer = findViewById(seekIds[band]);
            eqLabels[band] = findViewById(labelIds[band]);
            equalizer.setMax(24);
            equalizer.setProgress(12);
            equalizer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    eqValues[index] = progress - 12f;
                    eqLabels[index].setText(String.format(
                            java.util.Locale.US, "%+.0f dB", eqValues[index]));
                    if (engineReady) applyEqualizer();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    private void applyEqualizer() {
        nativeSetEqualizer(eqValues[0], eqValues[1], eqValues[2]);
    }

    private void changeOctave(int direction) {
        if (keyboard.hasActiveTouches()) {
            status.setText("Release the keys before changing octave");
            return;
        }
        int next = Math.max(-1, Math.min(1, octaveShift + direction));
        if (next == octaveShift) return;
        octaveShift = next;
        keyboard.setTransposeSemitones(octaveShift * 12);
        octaveLabel.setText("OCT " + (octaveShift > 0 ? "+" : "") + octaveShift);
    }

    private void playChord(int chordIndex, boolean on) {
        if (!engineReady || !presetReady) return;
        int[] intervals = CHORD_INTERVALS[chordIndex];
        for (int interval : intervals) {
            int note = CHORD_ROOTS[chordIndex] + interval;
            if (on) nativeNoteOn(0, note, 100);
            else nativeNoteOff(0, note);
        }
    }

    private void setupSoundFontPicker() {
        findViewById(R.id.btnLoadSoundFont).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_SOUNDFONT);
        });
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!engineReady || position < 0 || position >= presetOptions.size()) return;
                PresetOption option = presetOptions.get(position);
                int generation = ++presetLoadGeneration;
                presetReady = false;
                if (option.soundFont) {
                    if (soundFontLoaded && nativeSelectPreset(0, option.program)) {
                        presetReady = true;
                        activeInstrument.setText(option.label);
                        status.setText(selectedSoundFont + " • " + option.label);
                    } else {
                        status.setText("Could not select that SoundFont preset");
                    }
                } else {
                    status.setText("Loading " + option.label + "...");
                    sampleDecoder.execute(() -> loadInstrumentPreset(option, generation));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SOUNDFONT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            String path = copySoundFontToCache(uri);
            if (!engineReady || !nativeLoadSoundFont(path)) {
                status.setText("Could not load this SF2 SoundFont");
                Toast.makeText(this, "SoundFont loading failed", Toast.LENGTH_LONG).show();
                return;
            }
            selectedSoundFont = new File(path).getName();
            soundFontLoaded = true;
            loadPresetList();
        } catch (IOException error) {
            status.setText("Could not read the selected SoundFont");
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String copySoundFontToCache(Uri uri) throws IOException {
        File target = new File(getCacheDir(), "user-" + System.currentTimeMillis() + ".sf2");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Selected file could not be opened");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return target.getAbsolutePath();
    }

    private void loadPresetList() {
        if (samplePresetCount == 0) return;
        while (presetOptions.size() > samplePresetCount) {
            presetOptions.remove(presetOptions.size() - 1);
        }
        for (int i = 0; i < 128; i++) {
            String label = String.format(java.util.Locale.US, "SF2 • %03d • %s", i + 1,
                    GeneralMidiNames.name(i));
            presetOptions.add(PresetOption.soundFont(label, i));
        }
        ArrayAdapter<PresetOption> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, presetOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setEnabled(true);
        presetSpinner.setSelection(samplePresetCount);
        activeInstrument.setText(presetOptions.get(samplePresetCount).label);
    }

    private void loadBundledInstrumentPresets() {
        try {
            String[] files = getAssets().list(INSTRUMENT_ASSET_DIRECTORY);
            if (files == null || files.length == 0) {
                status.setText("No bundled instrument samples were found");
                return;
            }
            Arrays.sort(files, String.CASE_INSENSITIVE_ORDER);
            for (String file : files) {
                if (file.endsWith(".wav") || file.endsWith(".mp3")) {
                    presetOptions.add(PresetOption.sample(file, displayName(file)));
                }
            }
            samplePresetCount = presetOptions.size();
            if (samplePresetCount == 0) {
                status.setText("No supported WAV/MP3 instrument samples were found");
                return;
            }
            ArrayAdapter<PresetOption> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, presetOptions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            presetSpinner.setAdapter(adapter);
            presetSpinner.setEnabled(true);
            presetSpinner.setSelection(0);
            activeInstrument.setText(presetOptions.get(0).label);
        } catch (IOException error) {
            status.setText("Could not read the bundled instrument list");
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadInstrumentPreset(PresetOption option, int generation) {
        try {
            File source = copyInstrumentToCache(option.assetName);
            DecodedAudio audio = decodeAudio(source);
            runOnUiThread(() -> {
                if (isFinishing() || generation != presetLoadGeneration) return;
                if (nativeSetSample(audio.pcm, audio.sampleRate, audio.channels)) {
                    presetReady = true;
                    activeInstrument.setText(option.label);
                    status.setText(option.label + " • ready");
                } else {
                    status.setText("Could not load " + option.label);
                    Toast.makeText(this, "Instrument sample could not be loaded",
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (IOException | IllegalStateException error) {
            runOnUiThread(() -> {
                if (isFinishing() || generation != presetLoadGeneration) return;
                status.setText("Could not decode " + option.label);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private File copyInstrumentToCache(String assetName) throws IOException {
        File directory = new File(getCacheDir(), INSTRUMENT_ASSET_DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the instrument cache directory");
        }
        File target = new File(directory, assetName);
        if (target.isFile() && target.length() > 0) return target;
        try (InputStream input = getAssets().open(INSTRUMENT_ASSET_DIRECTORY + "/" + assetName);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException error) {
            target.delete();
            throw error;
        }
        return target;
    }

    private DecodedAudio decodeAudio(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean codecStarted = false;
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int sampleRate = 0;
        int channels = 0;
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat sourceFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat track = extractor.getTrackFormat(i);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    sourceFormat = track;
                    extractor.selectTrack(i);
                    break;
                }
            }
            if (sourceFormat == null) throw new IOException("No audio track in " + file.getName());
            String mime = sourceFormat.getString(MediaFormat.KEY_MIME);
            sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(sourceFormat, null, null, 0);
            codec.start();
            codecStarted = true;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            while (!outputEnded) {
                if (!inputEnded) {
                    int inputIndex = codec.dequeueInputBuffer(10_000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input == null) throw new IOException("Audio decoder input is unavailable");
                        int size = extractor.readSampleData(input, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue;
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                    continue;
                }
                if (outputIndex < 0) continue;

                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output != null && info.size > 0) {
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    byte[] chunk = new byte[info.size];
                    output.get(chunk);
                    decoded.write(chunk);
                }
                outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(outputIndex, false);
            }
        } finally {
            try {
                if (codecStarted) codec.stop();
            } finally {
                try {
                    if (codec != null) codec.release();
                } finally {
                    extractor.release();
                }
            }
        }
        return new DecodedAudio(toFloatSamples(decoded.toByteArray(), pcmEncoding),
                sampleRate, channels);
    }

    private float[] toFloatSamples(byte[] bytes, int encoding) throws IOException {
        int bytesPerSample;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) bytesPerSample = 4;
        else if (encoding == AudioFormat.ENCODING_PCM_8BIT) bytesPerSample = 1;
        else if (encoding == AudioFormat.ENCODING_PCM_16BIT) bytesPerSample = 2;
        else throw new IOException("Unsupported decoded PCM format: " + encoding);

        int sampleCount = bytes.length / bytesPerSample;
        float[] samples = new float[sampleCount];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                samples[i] = buffer.getFloat();
            } else if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
                samples[i] = ((buffer.get() & 0xFF) - 128) / 128.0f;
            } else {
                samples[i] = buffer.getShort() / 32768.0f;
            }
        }
        return samples;
    }

    private String displayName(String fileName) {
        int extension = fileName.lastIndexOf('.');
        return (extension > 0 ? fileName.substring(0, extension) : fileName).trim();
    }

    @Override
    public void onNoteOn(int midiNote) {
        if (engineReady && presetReady) nativeNoteOn(0, midiNote, 100);
    }

    @Override
    public void onNoteOff(int midiNote) {
        if (engineReady) nativeNoteOff(0, midiNote);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        sampleDecoder.shutdownNow();
        if (engineReady) {
            nativeDestroy();
            engineReady = false;
        }
        super.onDestroy();
    }

    private static final class PresetOption {
        final String label;
        final String assetName;
        final boolean soundFont;
        final int program;

        private PresetOption(String label, String assetName, boolean soundFont, int program) {
            this.label = label;
            this.assetName = assetName;
            this.soundFont = soundFont;
            this.program = program;
        }

        static PresetOption sample(String assetName, String label) {
            return new PresetOption(label, assetName, false, -1);
        }

        static PresetOption soundFont(String label, int program) {
            return new PresetOption(label, null, true, program);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class DecodedAudio {
        final float[] pcm;
        final int sampleRate;
        final int channels;

        DecodedAudio(float[] pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}
