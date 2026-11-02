package com.pramod.piano;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements PianoKeyboardView.NoteListener {
    private static final int REQUEST_SOUNDFONT = 4101;
    private static final int REQUEST_AUDIO_FILES = 4102;
    private static final int MAX_CUSTOM_PRESETS = 100;
    private static final int FAVORITE_BANK_COUNT = 10;
    private static final String CUSTOM_PRESETS_PREFS = "custom_audio_presets";
    private static final String FAVORITE_BANKS_PREFS = "favorite_instrument_banks";
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
    private LinearLayout favoriteBanksRow;
    private final TextView[] eqLabels = new TextView[3];
    private final float[] eqValues = new float[3];
    private boolean engineReady;
    private boolean soundFontLoaded;
    private boolean presetReady;
    private int octaveShift;
    private int samplePresetCount;
    private int presetLoadGeneration;
    private String selectedSoundFont;
    private MidiManager midiManager;
    private MidiDevice midiDevice;
    private MidiOutputPort midiDeviceOutput;
    private MidiInputPort midiDeviceInput;
    private MidiReceiver midiInputReceiver;
    private int midiDeviceId = -1;
    private final List<PresetOption> presetOptions = new ArrayList<>();
    private final List<PresetOption> sampleOptions = new ArrayList<>();
    private final List<Button> favoriteBankButtons = new ArrayList<>();
    private final ExecutorService sampleDecoder = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        favoriteBanksRow = findViewById(R.id.favoriteBanksRow);
        midiManager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        pitchBendView.setBendListener(value -> {
            if (engineReady) nativePitchBend(0, value);
        });
        setupChordButtons();
        setupSoundFontPicker();
        setupOctaveAndScrollLock();
        setupAudioControls();
        setupPresetControls();
        setupFavoriteBanks();
        setupMidiControls();

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
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> {
            keyboard.changeZoom(0.85f);
            keyboardScroll.post(() -> keyboardScroll.smoothScrollTo(
                    Math.max(0, keyboardScroll.getScrollX() / 2), 0));
        });
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> keyboard.changeZoom(1.18f));
    }

    private void setupPresetControls() {
        findViewById(R.id.btnPresetPrevious).setOnClickListener(v -> movePreset(-1));
        findViewById(R.id.btnPresetNext).setOnClickListener(v -> movePreset(1));
        findViewById(R.id.btnImportAudio).setOnClickListener(v -> openAudioPicker());
    }

    private void movePreset(int direction) {
        if (presetOptions.isEmpty()) return;
        int next = (presetSpinner.getSelectedItemPosition() + direction
                + presetOptions.size()) % presetOptions.size();
        presetSpinner.setSelection(next);
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_AUDIO_FILES);
    }

    private void setupFavoriteBanks() {
        favoriteBanksRow.removeAllViews();
        favoriteBankButtons.clear();
        for (int slot = 0; slot < FAVORITE_BANK_COUNT; slot++) {
            final int bank = slot;
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setTextSize(8f);
            button.setMinWidth(dp(38));
            button.setPadding(dp(2), 0, dp(2), 0);
            button.setBackgroundResource(R.drawable.chord_button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            params.setMargins(dp(2), dp(1), dp(2), dp(1));
            favoriteBanksRow.addView(button, params);
            favoriteBankButtons.add(button);
            button.setOnClickListener(view -> loadFavoriteBank(bank));
            button.setOnLongClickListener(view -> {
                saveFavoriteBank(bank);
                return true;
            });
        }
        refreshFavoriteBankLabels();
    }

    private String[] readFavoriteBankKeys() {
        String[] keys = new String[FAVORITE_BANK_COUNT];
        Set<String> stored = getSharedPreferences(FAVORITE_BANKS_PREFS, MODE_PRIVATE)
                .getStringSet("banks", Collections.emptySet());
        for (String item : stored) {
            int separator = item.indexOf('=');
            if (separator <= 0) continue;
            try {
                int slot = Integer.parseInt(item.substring(0, separator));
                if (slot >= 0 && slot < keys.length) keys[slot] = item.substring(separator + 1);
            } catch (NumberFormatException ignored) {
                // Ignore malformed locally stored bank indices.
            }
        }
        return keys;
    }

    private void persistFavoriteBankKeys(String[] keys) {
        Set<String> stored = new HashSet<>();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) stored.add(i + "=" + keys[i]);
        }
        getSharedPreferences(FAVORITE_BANKS_PREFS, MODE_PRIVATE).edit()
                .putStringSet("banks", stored).apply();
    }

    private void refreshFavoriteBankLabels() {
        if (favoriteBankButtons.size() != FAVORITE_BANK_COUNT) return;
        String[] keys = readFavoriteBankKeys();
        for (int i = 0; i < keys.length; i++) {
            PresetOption option = findPreset(keys[i]);
            favoriteBankButtons.get(i).setText(option == null
                    ? "F" + (i + 1) : option.shortLabel);
        }
    }

    private void saveFavoriteBank(int slot) {
        int selected = presetSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= presetOptions.size()) {
            status.setText("Select an instrument before saving a favorite");
            return;
        }
        PresetOption selectedOption = presetOptions.get(selected);
        if (selectedOption.soundFont) {
            status.setText("SF2 presets cannot be saved in favorite banks");
            return;
        }
        String[] keys = readFavoriteBankKeys();
        keys[slot] = selectedOption.key;
        persistFavoriteBankKeys(keys);
        refreshFavoriteBankLabels();
        status.setText("Saved " + selectedOption.label + " to F" + (slot + 1));
    }

    private void loadFavoriteBank(int slot) {
        PresetOption option = findPreset(readFavoriteBankKeys()[slot]);
        if (option == null) {
            status.setText("Long-press F" + (slot + 1) + " to save an instrument");
            return;
        }
        int position = presetOptions.indexOf(option);
        if (position >= 0) presetSpinner.setSelection(position);
    }

    private PresetOption findPreset(String key) {
        if (key == null) return null;
        for (PresetOption option : presetOptions) {
            if (key.equals(option.key)) return option;
        }
        return null;
    }

    private void setupMidiControls() {
        findViewById(R.id.btnMidiIn).setOnClickListener(v -> chooseMidiDevice(true));
        findViewById(R.id.btnMidiOut).setOnClickListener(v -> chooseMidiDevice(false));
    }

    private void chooseMidiDevice(boolean forInput) {
        if (midiManager == null) {
            status.setText("Android MIDI service is unavailable");
            return;
        }
        List<MidiDeviceInfo> available = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (MidiDeviceInfo info : midiManager.getDevices()) {
            int wantedType = forInput ? MidiDeviceInfo.PortInfo.TYPE_OUTPUT
                    : MidiDeviceInfo.PortInfo.TYPE_INPUT;
            int portCount = 0;
            for (MidiDeviceInfo.PortInfo port : info.getPorts()) {
                if (port.getType() == wantedType) portCount++;
            }
            if (portCount == 0) continue;
            available.add(info);
            CharSequence name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
            labels.add((name == null ? "USB MIDI device" : name.toString())
                    + " (" + portCount + " port" + (portCount == 1 ? "" : "s") + ")");
        }
        if (available.isEmpty()) {
            status.setText(forInput ? "No USB MIDI input devices found"
                    : "No USB MIDI output devices found");
            return;
        }
        String title = forInput ? "Connect MIDI keyboard (IN)" : "Connect MIDI device (OUT)";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) ->
                        openMidiDevice(available.get(which), forInput))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openMidiDevice(MidiDeviceInfo info, boolean forInput) {
        if (midiManager == null) return;
        if (midiDevice != null && midiDeviceId == info.getId()) {
            openMidiPort(info, forInput);
            return;
        }
        closeMidiDevice();
        status.setText("Connecting USB MIDI…");
        midiManager.openDevice(info, device -> {
            if (device == null) {
                status.setText("Could not open the USB MIDI device");
                return;
            }
            midiDevice = device;
            midiDeviceId = info.getId();
            openMidiPort(info, forInput);
        }, mainHandler);
    }

    private void openMidiPort(MidiDeviceInfo info, boolean forInput) {
        int wantedType = forInput ? MidiDeviceInfo.PortInfo.TYPE_OUTPUT
                : MidiDeviceInfo.PortInfo.TYPE_INPUT;
        MidiDeviceInfo.PortInfo selectedPort = null;
        for (MidiDeviceInfo.PortInfo port : info.getPorts()) {
            if (port.getType() == wantedType) {
                selectedPort = port;
                break;
            }
        }
        if (selectedPort == null || midiDevice == null) {
            status.setText("No compatible MIDI port is available");
            return;
        }
        if (forInput) {
            if (midiDeviceOutput != null) {
                midiDeviceOutput.close();
                midiDeviceOutput = null;
            }
            midiDeviceOutput = midiDevice.openOutputPort(selectedPort.getPortNumber());
            if (midiDeviceOutput == null) {
                status.setText("Could not open USB MIDI input port");
                return;
            }
            midiInputReceiver = new MidiReceiver() {
                @Override
                public void onSend(byte[] data, int offset, int count, long timestamp) {
                    receiveMidiMessages(data, offset, count);
                }
            };
            try {
                midiDeviceOutput.connect(midiInputReceiver);
                status.setText("USB MIDI IN connected");
            } catch (IOException error) {
                midiDeviceOutput.close();
                midiDeviceOutput = null;
                status.setText("Could not connect MIDI input: " + error.getMessage());
            }
        } else {
            if (midiDeviceInput != null) {
                midiDeviceInput.close();
                midiDeviceInput = null;
            }
            midiDeviceInput = midiDevice.openInputPort(selectedPort.getPortNumber());
            status.setText(midiDeviceInput == null
                    ? "Could not open USB MIDI output port" : "USB MIDI OUT connected");
        }
    }

    private void receiveMidiMessages(byte[] data, int offset, int count) {
        int end = offset + count;
        int cursor = offset;
        while (cursor < end) {
            int statusByte = data[cursor] & 0xFF;
            if (statusByte < 0x80 || statusByte >= 0xF0) {
                cursor++;
                continue;
            }
            int command = statusByte & 0xF0;
            int channel = statusByte & 0x0F;
            int messageLength = command == 0xC0 || command == 0xD0 ? 2 : 3;
            if (cursor + messageLength > end) return;
            int data1 = data[cursor + 1] & 0x7F;
            int data2 = messageLength == 3 ? data[cursor + 2] & 0x7F : 0;
            if (command == 0x90 && data2 > 0) {
                if (engineReady && presetReady) nativeNoteOn(channel, data1, data2);
                mainHandler.post(() -> keyboard.setExternalNote(data1, true));
            } else if (command == 0x80 || (command == 0x90 && data2 == 0)) {
                if (engineReady) nativeNoteOff(channel, data1);
                mainHandler.post(() -> keyboard.setExternalNote(data1, false));
            } else if (command == 0xE0 && engineReady) {
                nativePitchBend(channel, data1 | (data2 << 7));
            }
            cursor += messageLength;
        }
    }

    private void sendMidiNote(boolean on, int note, int velocity) {
        if (midiDeviceInput == null) return;
        byte[] message = {
                (byte) ((on ? 0x90 : 0x80) & 0xFF),
                (byte) (note & 0x7F),
                (byte) (on ? velocity & 0x7F : 0)
        };
        try {
            midiDeviceInput.send(message, 0, message.length, System.nanoTime());
        } catch (IOException error) {
            status.setText("MIDI OUT failed: " + error.getMessage());
            midiDeviceInput.close();
            midiDeviceInput = null;
        }
    }

    private void closeMidiDevice() {
        if (midiDeviceOutput != null) {
            midiDeviceOutput.close();
            midiDeviceOutput = null;
        }
        if (midiDeviceInput != null) {
            midiDeviceInput.close();
            midiDeviceInput = null;
        }
        midiInputReceiver = null;
        if (midiDevice != null) {
            midiDevice.close();
            midiDevice = null;
        }
        midiDeviceId = -1;
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
            if (on) {
                nativeNoteOn(0, note, 100);
                sendMidiNote(true, note, 100);
            } else {
                nativeNoteOff(0, note);
                sendMidiNote(false, note, 0);
            }
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
        if (requestCode == REQUEST_AUDIO_FILES && resultCode == RESULT_OK && data != null) {
            importAudioSelection(data);
            return;
        }
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

    private void importAudioSelection(Intent data) {
        List<Uri> selectedUris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                selectedUris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selectedUris.add(data.getData());
        }
        if (selectedUris.isEmpty()) return;

        sampleDecoder.execute(() -> {
            List<PresetOption> imported = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (Uri uri : selectedUris) {
                if (customPresetCount() + imported.size() >= MAX_CUSTOM_PRESETS) {
                    failures.add("Library limit reached (100 custom sounds)");
                    break;
                }
                try {
                    imported.add(copyImportedAudio(uri));
                } catch (IOException | IllegalArgumentException error) {
                    failures.add(error.getMessage() == null
                            ? "Could not import a selected audio file" : error.getMessage());
                }
            }
            if (imported.isEmpty()) {
                runOnUiThread(() -> status.setText(failures.isEmpty()
                        ? "No supported audio files selected" : failures.get(0)));
                return;
            }
            runOnUiThread(() -> {
                sampleOptions.addAll(imported);
                rebuildPresetOptions();
                if (!failures.isEmpty()) {
                    status.setText(imported.size() + " sound(s) imported; "
                            + failures.size() + " could not be added");
                } else {
                    status.setText(imported.size() + " sound(s) added to your library");
                }
                presetSpinner.setSelection(presetOptions.size() - 1);
            });
        });
    }

    private PresetOption copyImportedAudio(Uri uri) throws IOException {
        String displayName = queryDisplayName(uri);
        String lowerName = displayName.toLowerCase(java.util.Locale.ROOT);
        String extension;
        if (lowerName.endsWith(".wav")) {
            extension = ".wav";
        } else if (lowerName.endsWith(".mp3")) {
            extension = ".mp3";
        } else {
            throw new IllegalArgumentException("Only WAV and MP3 files are supported: "
                    + displayName);
        }

        File directory = new File(getFilesDir(), "custom-instruments");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the custom instrument library");
        }
        String storedFile = UUID.randomUUID().toString() + extension;
        File target = new File(directory, storedFile);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Could not open " + displayName);
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException error) {
            if (target.exists() && !target.delete()) {
                android.util.Log.w("Piano", "Could not remove incomplete imported audio");
            }
            throw error;
        }
        PresetOption option = PresetOption.custom(storedFile, displayName);
        Set<String> stored = new HashSet<>(getSharedPreferences(
                CUSTOM_PRESETS_PREFS, MODE_PRIVATE).getStringSet("files", Collections.emptySet()));
        stored.add(storedFile + "|" + displayName);
        getSharedPreferences(CUSTOM_PRESETS_PREFS, MODE_PRIVATE).edit()
                .putStringSet("files", stored).apply();
        return option;
    }

    private String queryDisplayName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return name == null ? "Imported instrument.wav" : name;
    }

    private int customPresetCount() {
        return getSharedPreferences(CUSTOM_PRESETS_PREFS, MODE_PRIVATE)
                .getStringSet("files", Collections.emptySet()).size();
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
        refreshFavoriteBankLabels();
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
                String lower = file.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".wav") || lower.endsWith(".mp3")) {
                    sampleOptions.add(PresetOption.sample(file, displayName(file)));
                }
            }
            List<String> customEntries = new ArrayList<>(getSharedPreferences(
                    CUSTOM_PRESETS_PREFS, MODE_PRIVATE)
                    .getStringSet("files", Collections.emptySet()));
            Collections.sort(customEntries, String.CASE_INSENSITIVE_ORDER);
            for (String entry : customEntries) {
                int separator = entry.indexOf('|');
                if (separator <= 0) continue;
                String fileName = entry.substring(0, separator);
                String label = entry.substring(separator + 1);
                File storedFile = new File(getFilesDir(), "custom-instruments/" + fileName);
                if (storedFile.isFile() && storedFile.length() > 0) {
                    sampleOptions.add(PresetOption.custom(fileName, label));
                }
            }
            rebuildPresetOptions();
            samplePresetCount = presetOptions.size();
            if (samplePresetCount == 0) {
                status.setText("No supported WAV/MP3 instrument samples were found");
                return;
            }
            bindPresetSpinner();
            presetSpinner.setSelection(0);
            refreshFavoriteBankLabels();
        } catch (IOException error) {
            status.setText("Could not read the bundled instrument list");
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void rebuildPresetOptions() {
        presetOptions.clear();
        presetOptions.addAll(sampleOptions);
        samplePresetCount = sampleOptions.size();
        bindPresetSpinner();
        refreshFavoriteBankLabels();
    }

    private void bindPresetSpinner() {
        ArrayAdapter<PresetOption> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, presetOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setEnabled(!presetOptions.isEmpty());
    }

    private void loadInstrumentPreset(PresetOption option, int generation) {
        try {
            File source = option.customFileName == null
                    ? copyInstrumentToCache(option.assetName)
                    : new File(getFilesDir(), "custom-instruments/" + option.customFileName);
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
        sendMidiNote(true, midiNote, 100);
    }

    @Override
    public void onNoteOff(int midiNote) {
        if (engineReady) nativeNoteOff(0, midiNote);
        sendMidiNote(false, midiNote, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        sampleDecoder.shutdownNow();
        closeMidiDevice();
        if (engineReady) {
            nativeDestroy();
            engineReady = false;
        }
        super.onDestroy();
    }

    private static final class PresetOption {
        final String label;
        final String shortLabel;
        final String key;
        final String assetName;
        final String customFileName;
        final boolean soundFont;
        final int program;

        private PresetOption(String label, String shortLabel, String key, String assetName,
                             String customFileName, boolean soundFont, int program) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.key = key;
            this.assetName = assetName;
            this.customFileName = customFileName;
            this.soundFont = soundFont;
            this.program = program;
        }

        static PresetOption sample(String assetName, String label) {
            return new PresetOption(label, compactLabel(label), "asset:" + assetName,
                    assetName, null, false, -1);
        }

        static PresetOption custom(String fileName, String label) {
            return new PresetOption(label, compactLabel(label), "custom:" + fileName,
                    null, fileName, false, -1);
        }

        static PresetOption soundFont(String label, int program) {
            return new PresetOption(label, "SF" + (program + 1), "sf2:" + program,
                    null, null, true, program);
        }

        private static String compactLabel(String label) {
            String compact = label.trim();
            return compact.length() > 7 ? compact.substring(0, 7) : compact;
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
