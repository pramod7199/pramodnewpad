package com.pramod.loopmidi;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

final class PadThemeCustomizer {
    static final int REQUEST_PAD_IMAGE = 5011;
    static final int REQUEST_BACKGROUND_IMAGE = 5012;
    static final int REQUEST_COMBINED_IMAGE = 5013;
    private static final String KEY_PRESET = "theme_preset";
    private static final String KEY_BORDER_ALL = "theme_border_all";
    private static final String KEY_BORDER_COLOR = "theme_border_color_";
    private static final String KEY_BORDER_PRESET = "theme_border_preset_";

    private static final String TAG = "PadThemeCustomizer";
    private static final String KEY_PAD_IMAGE = "theme_pad_image_";
    private static final String KEY_PAD_OPACITY = "theme_pad_opacity_";
    private static final String KEY_PAD_LABEL_VISIBLE = "theme_pad_label_visible";
    private static final String KEY_PAD_LABEL = "theme_pad_label_";
    private static final String KEY_PAD_EMOJI = "theme_pad_emoji_";
    private static final String KEY_COMBINED_IMAGE = "theme_combined_image";
    private static final String KEY_BACKGROUND_IMAGE = "theme_background_image";
    private static final String KEY_BACKGROUND_OPACITY = "theme_background_opacity";
    private static final String KEY_ANIMATION_MODE = "theme_animation_mode";
    private static final int MODE_IMAGE_PULSE = 0;
    private static final int MODE_SHADOW_PULSE = 1;
    private static final int BORDER_NONE = 0;
    private static final int BORDER_NEON = 1;
    private static final int BORDER_RED = 2;
    private static final int BORDER_GREEN = 3;
    private static final int BORDER_YELLOW = 4;
    private static final int BORDER_BLUE = 5;
    private static final int BORDER_PURPLE = 6;
    private static final int BORDER_RGB = 7;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Button[] pads;
    private final Drawable[] basePadBackgrounds;
    private final View root;
    private final View[] backgroundSurfaces;
    private final Drawable[] baseSurfaceBackgrounds;
    private final Button[] toggleButtons;
    private Bitmap togglePhoto;
    private String togglePhotoUri;
    private int pickerPadIndex = -1;
    private boolean applyImageToAll;

    PadThemeCustomizer(Activity activity, SharedPreferences preferences, Button[] pads,
                       View root) {
        this.activity = activity;
        this.preferences = preferences;
        this.pads = pads;
        this.root = root;
        this.basePadBackgrounds = new Drawable[pads.length];
        for (int i = 0; i < pads.length; i++) {
            basePadBackgrounds[i] = pads[i].getBackground();
        }
        int[] surfaceIds = {R.id.topControlBar, R.id.fxControlBar, R.id.advControlBar,
                R.id.favDrumRow, R.id.titleBar, R.id.padArea};
        backgroundSurfaces = new View[surfaceIds.length];
        baseSurfaceBackgrounds = new Drawable[surfaceIds.length];
        for (int i = 0; i < surfaceIds.length; i++) {
            backgroundSurfaces[i] = root.findViewById(surfaceIds[i]);
            if (backgroundSurfaces[i] != null) {
                baseSurfaceBackgrounds[i] = backgroundSurfaces[i].getBackground();
            }
        }
        int[] toggleIds = {R.id.btnPrevKit, R.id.btnNextKit, R.id.btnEditMode,
                R.id.btnThemeToggle, R.id.btnRenameKit, R.id.btnEq, R.id.btnPadEdit,
                R.id.btnSaveKit, R.id.btnLoadKit, R.id.btnVelocity, R.id.btnMidiMap,
                R.id.btnBankA, R.id.btnBankB, R.id.btnBankAB, R.id.btnMidiConnect,
                R.id.btnCCCtrl};
        java.util.ArrayList<Button> foundToggles = new java.util.ArrayList<>();
        for (int toggleId : toggleIds) {
            Button toggle = root.findViewById(toggleId);
            if (toggle != null) foundToggles.add(toggle);
        }
        toggleButtons = foundToggles.toArray(new Button[0]);
    }

    void applySavedTheme() {
        for (int i = 0; i < pads.length; i++) {
            applyPadImage(i);
        }
        applyGlobalBackground();
        applyPadLabels();
        applyTogglePhoto();
    }

    void showDialog(int initialPadIndex) {
        final int[] selectedPad = {
                Math.max(0, Math.min(pads.length - 1, initialPadIndex))
        };
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);

        addSectionTitle(content, "APP THEME PRESET");
        RadioGroup presets = new RadioGroup(activity);
        presets.setOrientation(RadioGroup.VERTICAL);
        String[] presetNames = {"Default grey-black", "Neon", "Red", "Light combo",
                "Purple haze"};
        int[] presetIds = new int[presetNames.length];
        for (int i = 0; i < presetNames.length; i++) {
            RadioButton preset = new RadioButton(activity);
            preset.setText(presetNames[i]);
            preset.setId(View.generateViewId());
            presetIds[i] = preset.getId();
            presets.addView(preset);
        }
        int activePreset = preferences.getInt(KEY_PRESET, 0);
        presets.check(presetIds[activePreset >= 0 && activePreset < presetIds.length
                ? activePreset : 0]);
        presets.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < presetIds.length; i++) {
                if (presetIds[i] == checkedId) {
                    applyPreset(i);
                    break;
                }
            }
        });
        content.addView(presets);

        addSectionTitle(content, "INDIVIDUAL PAD CUSTOMIZATION");
        LinearLayout padPicker = new LinearLayout(activity);
        padPicker.setOrientation(LinearLayout.HORIZONTAL);
        Button[] padButtons = new Button[pads.length];
        TextView[] padOpacityLabel = new TextView[1];
        SeekBar[] padOpacityControl = new SeekBar[1];
        EditText[] padNameField = new EditText[1];
        EditText[] padEmojiField = new EditText[1];
        for (int i = 0; i < pads.length; i++) {
            final int padIndex = i;
            Button choosePad = new Button(activity);
            choosePad.setText(Integer.toString(i + 1));
            padButtons[i] = choosePad;
            padPicker.addView(choosePad, new LinearLayout.LayoutParams(
                    0, dp(44), 1f));
            choosePad.setOnClickListener(v -> {
                selectedPad[0] = padIndex;
                updateSelectedPadButtons(padButtons, padIndex);
                int opacity = preferences.getInt(KEY_PAD_OPACITY + padIndex, 70);
                padOpacityLabel[0].setText("Pad image opacity: " + opacity + "%");
                padOpacityControl[0].setProgress(opacity);
                padNameField[0].setText(getPadLabel(padIndex));
                padEmojiField[0].setText(preferences.getString(KEY_PAD_EMOJI + padIndex, ""));
            });
        }
        updateSelectedPadButtons(padButtons, selectedPad[0]);
        content.addView(padPicker);
        Button choosePadImage = addButton(content, "Choose image for selected pad");
        choosePadImage.setOnClickListener(v -> {
            pickerPadIndex = selectedPad[0];
            applyImageToAll = false;
            launchPicker(REQUEST_PAD_IMAGE);
        });
        Button chooseAllPadImages = addButton(content, "Apply one image to all 8 pads");
        chooseAllPadImages.setOnClickListener(v -> {
            pickerPadIndex = selectedPad[0];
            applyImageToAll = true;
            launchPicker(REQUEST_PAD_IMAGE);
        });
        Button clearPadImage = addButton(content, "Remove pad image");
        clearPadImage.setOnClickListener(v -> {
            preferences.edit().remove(KEY_PAD_IMAGE + selectedPad[0]).apply();
            applyPadImage(selectedPad[0]);
            Toast.makeText(activity, "Pad image removed", Toast.LENGTH_SHORT).show();
        });
        padOpacityControl[0] = addOpacityControl(content, "Pad image opacity",
                preferences.getInt(KEY_PAD_OPACITY + selectedPad[0], 70),
                progress -> setPadOpacity(selectedPad[0], progress), padOpacityLabel);
        Button removeAllPadImages = addButton(content, "Remove images from all 8 pads");
        removeAllPadImages.setOnClickListener(v -> {
            clearPadImages();
            Toast.makeText(activity, "Images removed from all pads", Toast.LENGTH_SHORT).show();
        });

        addSectionTitle(content, "PAD LABELS");
        CheckBox showPadLabels = new CheckBox(activity);
        showPadLabels.setText("Show pad text labels");
        showPadLabels.setChecked(preferences.getBoolean(KEY_PAD_LABEL_VISIBLE, true));
        showPadLabels.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(KEY_PAD_LABEL_VISIBLE, checked).apply();
            applyPadLabels();
        });
        content.addView(showPadLabels);
        EditText padName = new EditText(activity);
        padName.setSingleLine(true);
        padName.setHint("Selected pad name");
        padName.setText(getPadLabel(selectedPad[0]));
        padNameField[0] = padName;
        content.addView(padName);
        EditText padEmoji = new EditText(activity);
        padEmoji.setSingleLine(true);
        padEmoji.setHint("Emoji (optional)");
        padEmoji.setText(preferences.getString(KEY_PAD_EMOJI + selectedPad[0], ""));
        padEmojiField[0] = padEmoji;
        content.addView(padEmoji);
        Button savePadLabel = addButton(content, "Save name and emoji for selected pad");
        savePadLabel.setOnClickListener(v -> {
            String name = padName.getText().toString().trim();
            String emoji = padEmoji.getText().toString().trim();
            if (name.isEmpty()) name = "PAD " + (selectedPad[0] + 1);
            preferences.edit()
                    .putString(KEY_PAD_LABEL + selectedPad[0], name)
                    .putString(KEY_PAD_EMOJI + selectedPad[0], emoji)
                    .apply();
            applyPadLabel(selectedPad[0]);
            Toast.makeText(activity, "Pad label saved", Toast.LENGTH_SHORT).show();
        });

        addSectionTitle(content, "COMBINED PHOTO");
        Button chooseCombinedImage = addButton(content,
                "Apply one photo to all 8 pads and the background");
        chooseCombinedImage.setOnClickListener(v -> {
            launchPicker(REQUEST_COMBINED_IMAGE);
        });
        Button clearCombinedImage = addButton(content,
                "Remove combined photo from all pads and background");
        clearCombinedImage.setOnClickListener(v -> clearCombinedImage());

        CheckBox sameBorderForAll = new CheckBox(activity);
        sameBorderForAll.setText("Global mode: apply border preset to all 8 pads");
        sameBorderForAll.setChecked(preferences.getBoolean(KEY_BORDER_ALL, true));
        content.addView(sameBorderForAll);
        addSectionTitle(content, "PAD TOUCH BORDER COLOR");
        TextView borderModeHint = new TextView(activity);
        borderModeHint.setText("Individual mode uses the selected pad above.");
        content.addView(borderModeHint);
        String[] borderNames = {"Neon", "Red", "Green", "Yellow",
                "Blue", "Purple", "RGB", "None"};
        int[] borderPresets = {BORDER_NEON, BORDER_RED, BORDER_GREEN, BORDER_YELLOW,
                BORDER_BLUE, BORDER_PURPLE, BORDER_RGB, BORDER_NONE};
        for (int row = 0; row < 2; row++) {
            LinearLayout borderColors = new LinearLayout(activity);
            borderColors.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                addBorderButton(borderColors, borderNames[index], borderPresets[index],
                        selectedPad, sameBorderForAll);
            }
            content.addView(borderColors);
        }
        sameBorderForAll.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(KEY_BORDER_ALL, checked).apply());

        addSectionTitle(content, "GLOBAL LAYOUT BACKGROUND");
        Button chooseBackground = addButton(content, "Choose layout background image");
        chooseBackground.setOnClickListener(v -> launchPicker(REQUEST_BACKGROUND_IMAGE));
        Button clearBackground = addButton(content, "Remove background image");
        clearBackground.setOnClickListener(v -> {
            preferences.edit().remove(KEY_BACKGROUND_IMAGE).apply();
            applyGlobalBackground();
            Toast.makeText(activity, "Background image removed", Toast.LENGTH_SHORT).show();
        });
        addOpacityControl(content, "Background image opacity",
                preferences.getInt(KEY_BACKGROUND_OPACITY, 70),
                this::setBackgroundOpacity, null);

        addSectionTitle(content, "HIT ANIMATION");
        RadioGroup animationModes = new RadioGroup(activity);
        animationModes.setOrientation(RadioGroup.VERTICAL);
        RadioButton imagePulse = new RadioButton(activity);
        imagePulse.setText("Image animation over the pad image");
        imagePulse.setId(View.generateViewId());
        RadioButton shadowPulse = new RadioButton(activity);
        shadowPulse.setText("Shadow animation when a pad is hit");
        shadowPulse.setId(View.generateViewId());
        animationModes.addView(imagePulse);
        animationModes.addView(shadowPulse);
        int mode = preferences.getInt(KEY_ANIMATION_MODE, MODE_IMAGE_PULSE);
        animationModes.check(mode == MODE_SHADOW_PULSE
                ? shadowPulse.getId() : imagePulse.getId());
        animationModes.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = checkedId == shadowPulse.getId()
                    ? MODE_SHADOW_PULSE : MODE_IMAGE_PULSE;
            preferences.edit().putInt(KEY_ANIMATION_MODE, selectedMode).apply();
        });
        content.addView(animationModes);

        new AlertDialog.Builder(activity)
                .setTitle("Custom Theme")
                .setView(scrollView)
                .setPositiveButton("Done", null)
                .show();
    }

    boolean handlesPickerRequest(int requestCode) {
        return requestCode == REQUEST_PAD_IMAGE || requestCode == REQUEST_BACKGROUND_IMAGE
                || requestCode == REQUEST_COMBINED_IMAGE;
    }

    void handlePickerResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
                throw new IOException("Image permission was not granted");
            }
            activity.getContentResolver().takePersistableUriPermission(uri, flags);
            if (requestCode == REQUEST_PAD_IMAGE) {
                if (pickerPadIndex < 0 || pickerPadIndex >= pads.length) {
                    throw new IOException("No pad selected for the image");
                }
                SharedPreferences.Editor editor = preferences.edit();
                if (applyImageToAll) {
                    for (int i = 0; i < pads.length; i++) {
                        editor.putString(KEY_PAD_IMAGE + i, uri.toString());
                    }
                } else {
                    editor.putString(KEY_PAD_IMAGE + pickerPadIndex, uri.toString());
                }
                editor.apply();
                if (applyImageToAll) {
                    for (int i = 0; i < pads.length; i++) applyPadImage(i);
                } else {
                    applyPadImage(pickerPadIndex);
                }
            } else if (requestCode == REQUEST_BACKGROUND_IMAGE) {
                preferences.edit().putString(KEY_BACKGROUND_IMAGE, uri.toString()).apply();
                applyGlobalBackground();
            } else if (requestCode == REQUEST_COMBINED_IMAGE) {
                SharedPreferences.Editor editor = preferences.edit()
                        .putString(KEY_BACKGROUND_IMAGE, uri.toString())
                        .putString(KEY_COMBINED_IMAGE, uri.toString());
                for (int i = 0; i < pads.length; i++) {
                    editor.putString(KEY_PAD_IMAGE + i, uri.toString());
                }
                editor.apply();
                for (int i = 0; i < pads.length; i++) applyPadImage(i);
                applyGlobalBackground();
            }
            if (requestCode == REQUEST_COMBINED_IMAGE) applyTogglePhoto();
        } catch (Exception error) {
            Log.e(TAG, "Could not apply selected theme image", error);
            Toast.makeText(activity, "Could not use that image: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    void onPadHit(int padIndex, View pad) {
        animateTouchBorder(padIndex);
        int mode = preferences.getInt(KEY_ANIMATION_MODE, MODE_IMAGE_PULSE);
        if (mode == MODE_SHADOW_PULSE) {
            float raisedZ = dp(12);
            AnimatorSet animation = new AnimatorSet();
            animation.playTogether(
                    ObjectAnimator.ofFloat(pad, View.TRANSLATION_Z, 0f, raisedZ, 0f),
                    ObjectAnimator.ofFloat(pad, View.SCALE_X, 1f, 0.975f, 1f),
                    ObjectAnimator.ofFloat(pad, View.SCALE_Y, 1f, 0.975f, 1f));
            animation.setDuration(190);
            animation.start();
            return;
        }

        Drawable background = pads[padIndex].getBackground();
        if (!(background instanceof LayerDrawable)) return;
        LayerDrawable layers = (LayerDrawable) background;
        BitmapDrawable image = null;
        for (int i = 1; i < layers.getNumberOfLayers(); i++) {
            if (layers.getDrawable(i) instanceof BitmapDrawable) {
                image = (BitmapDrawable) layers.getDrawable(i);
                break;
            }
        }
        if (image == null) return;
        int restingAlpha = preferences.getInt(KEY_PAD_OPACITY + padIndex, 70) * 255 / 100;
        ObjectAnimator pulse = ObjectAnimator.ofInt(
                image, "alpha", restingAlpha, Math.min(255, restingAlpha + 90), restingAlpha);
        pulse.setDuration(180);
        pulse.start();
    }

    private void launchPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
    }

    private void applyPadImage(int padIndex) {
        Drawable base = createPadBase(padIndex);
        String uriText = preferences.getString(KEY_PAD_IMAGE + padIndex, null);
        if (uriText == null) {
            pads[padIndex].setBackground(addPadBorder(padIndex, base));
            return;
        }
        try {
            Bitmap bitmap = decodeImage(Uri.parse(uriText), 1024);
            BitmapDrawable image = new BitmapDrawable(activity.getResources(), bitmap);
            image.setAlpha(preferences.getInt(KEY_PAD_OPACITY + padIndex, 70) * 255 / 100);
            int borderColor = getBorderColor(padIndex);
            Drawable border = createPadBorder(borderColor);
            pads[padIndex].setBackground(borderColor == 0
                    ? new LayerDrawable(new Drawable[]{base, image})
                    : new LayerDrawable(new Drawable[]{base, image, border}));
        } catch (Exception error) {
            Log.e(TAG, "Could not load pad theme image for pad " + (padIndex + 1), error);
            preferences.edit().remove(KEY_PAD_IMAGE + padIndex).apply();
            pads[padIndex].setBackground(addPadBorder(padIndex, createPadBase(padIndex)));
            Toast.makeText(activity, "Saved image for PAD " + (padIndex + 1)
                    + " is unavailable", Toast.LENGTH_LONG).show();
        }
    }

    private void applyGlobalBackground() {
        String uriText = preferences.getString(KEY_BACKGROUND_IMAGE, null);
        if (uriText == null) {
            root.setBackgroundColor(presetRootColor());
            applyPresetSurfaceBackgrounds();
            return;
        }
        try {
            Bitmap bitmap = decodeImage(Uri.parse(uriText), 1600);
            BitmapDrawable image = new BitmapDrawable(activity.getResources(), bitmap);
            image.setAlpha(preferences.getInt(KEY_BACKGROUND_OPACITY, 70) * 255 / 100);
            root.setBackground(new LayerDrawable(new Drawable[]{
                    new android.graphics.drawable.ColorDrawable(presetRootColor()), image
            }));
            for (View surface : backgroundSurfaces) {
                if (surface != null) surface.setBackgroundColor(Color.TRANSPARENT);
            }
        } catch (Exception error) {
            Log.e(TAG, "Could not load global theme background", error);
            SharedPreferences.Editor editor = preferences.edit().remove(KEY_BACKGROUND_IMAGE);
            if (uriText.equals(preferences.getString(KEY_COMBINED_IMAGE, null))) {
                editor.remove(KEY_COMBINED_IMAGE);
            }
            editor.apply();
            root.setBackgroundColor(presetRootColor());
            applyPresetSurfaceBackgrounds();
            applyTogglePhoto();
            Toast.makeText(activity, "Saved background image is unavailable",
                    Toast.LENGTH_LONG).show();
        }
    }

    void setToggleStyle(Button button, int styleResource) {
        if (button == null) return;
        Drawable style = activity.getDrawable(styleResource);
        Bitmap photo = getTogglePhoto();
        if (photo == null) {
            button.setBackground(style);
            return;
        }
        BitmapDrawable image = new BitmapDrawable(activity.getResources(), photo);
        image.setAlpha(72);
        button.setBackground(new LayerDrawable(new Drawable[]{style, image}));
    }

    private void applyTogglePhoto() {
        Bitmap photo = getTogglePhoto();
        for (Button button : toggleButtons) {
            Drawable current = button.getBackground();
            Drawable base = current;
            if (current instanceof LayerDrawable) {
                LayerDrawable layers = (LayerDrawable) current;
                base = layers.getDrawable(0);
            }
            if (photo == null) {
                button.setBackground(cloneDrawable(base));
            } else {
                BitmapDrawable image = new BitmapDrawable(activity.getResources(), photo);
                image.setAlpha(72);
                button.setBackground(new LayerDrawable(
                        new Drawable[]{cloneDrawable(base), image}));
            }
        }
    }

    private Bitmap getTogglePhoto() {
        String uriText = preferences.getString(KEY_COMBINED_IMAGE, null);
        if (uriText == null) {
            togglePhoto = null;
            togglePhotoUri = uriText;
            return null;
        }
        if (uriText.equals(togglePhotoUri)) return togglePhoto;
        try {
            togglePhoto = decodeImage(Uri.parse(uriText), 512);
            togglePhotoUri = uriText;
            return togglePhoto;
        } catch (IOException error) {
            Log.e(TAG, "Could not load combined photo for toggle styling", error);
            togglePhoto = null;
            togglePhotoUri = uriText;
            return null;
        }
    }

    private Bitmap decodeImage(Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image could not be opened");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Selected file is not a supported image");
        }
        int sampleSize = 1;
        while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize)
                > maxDimension) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image could not be opened");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Selected file is not a supported image");
            return bitmap;
        }
    }

    private void setPadOpacity(int padIndex, int progress) {
        int opacity = clamp(progress);
        preferences.edit().putInt(KEY_PAD_OPACITY + padIndex, opacity).apply();
        Drawable background = pads[padIndex].getBackground();
        if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background;
            if (layers.getNumberOfLayers() > 1
                    && layers.getDrawable(1) instanceof BitmapDrawable) {
                layers.getDrawable(1).setAlpha(opacity * 255 / 100);
                pads[padIndex].invalidate();
            }
        }
    }

    private void setBackgroundOpacity(int progress) {
        int opacity = clamp(progress);
        preferences.edit().putInt(KEY_BACKGROUND_OPACITY, opacity).apply();
        Drawable background = root.getBackground();
        if (background instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) background;
            if (layers.getNumberOfLayers() > 1
                    && layers.getDrawable(1) instanceof BitmapDrawable) {
                layers.getDrawable(1).setAlpha(opacity * 255 / 100);
                root.invalidate();
            }
        }
    }

    private SeekBar addOpacityControl(LinearLayout content, String label, int current,
                                      ProgressAction action, TextView[] labelOut) {
        TextView title = new TextView(activity);
        title.setText(label + ": " + clamp(current) + "%");
        content.addView(title);
        if (labelOut != null) labelOut[0] = title;
        SeekBar opacity = new SeekBar(activity);
        opacity.setMax(100);
        opacity.setProgress(clamp(current));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                title.setText(label + ": " + progress + "%");
                if (fromUser) action.onProgressChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        content.addView(opacity);
        return opacity;
    }

    private void addSectionTitle(LinearLayout content, String title) {
        TextView section = new TextView(activity);
        section.setText(title);
        section.setTextSize(15);
        section.setPadding(0, dp(14), 0, dp(4));
        content.addView(section);
    }

    private Button addButton(LinearLayout content, String label) {
        Button button = new Button(activity);
        button.setText(label);
        content.addView(button);
        return button;
    }

    private Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable.mutate() : state.newDrawable(activity.getResources()).mutate();
    }

    private void applyPreset(int preset) {
        preferences.edit().putInt(KEY_PRESET, preset).apply();
        applyGlobalBackground();
        for (int i = 0; i < pads.length; i++) applyPadImage(i);
    }

    private void clearPadImages() {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < pads.length; i++) editor.remove(KEY_PAD_IMAGE + i);
        editor.apply();
        for (int i = 0; i < pads.length; i++) applyPadImage(i);
    }

    private String getPadLabel(int padIndex) {
        return preferences.getString(KEY_PAD_LABEL + padIndex, "PAD " + (padIndex + 1));
    }

    private void applyPadLabels() {
        for (int i = 0; i < pads.length; i++) applyPadLabel(i);
    }

    private void applyPadLabel(int padIndex) {
        if (!preferences.getBoolean(KEY_PAD_LABEL_VISIBLE, true)) {
            pads[padIndex].setText("");
            return;
        }
        String emoji = preferences.getString(KEY_PAD_EMOJI + padIndex, "").trim();
        String label = getPadLabel(padIndex);
        pads[padIndex].setText(emoji.isEmpty() ? label : emoji + "\n" + label);
    }

    private void restoreBackgroundSurfaces() {
        for (int i = 0; i < backgroundSurfaces.length; i++) {
            if (backgroundSurfaces[i] != null) {
                backgroundSurfaces[i].setBackground(cloneDrawable(baseSurfaceBackgrounds[i]));
            }
        }
    }

    private void applyPresetSurfaceBackgrounds() {
        int preset = preferences.getInt(KEY_PRESET, 0);
        if (preset == 0) {
            restoreBackgroundSurfaces();
            return;
        }
        int color;
        switch (preset) {
            case 1: color = 0xFF081923; break;
            case 2: color = 0xFF260B0B; break;
            case 4: color = 0xFF21152E; break;
            default: color = 0xFF263448; break;
        }
        for (int i = 0; i < backgroundSurfaces.length; i++) {
            View surface = backgroundSurfaces[i];
            if (surface != null) {
                if (i == backgroundSurfaces.length - 1) {
                    surface.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    surface.setBackgroundColor(color);
                }
            }
        }
    }

    private int presetRootColor() {
        return presetRootColor(preferences.getInt(KEY_PRESET, 0));
    }

    private int presetRootColor(int preset) {
        switch (preset) {
            case 1: return 0xFF050C14;
            case 2: return 0xFF180707;
            case 3: return 0xFFE9EDF2;
            case 4: return 0xFF160D21;
            default: return 0xFF111111;
        }
    }

    private int presetPadColor() {
        switch (preferences.getInt(KEY_PRESET, 0)) {
            case 1: return 0xFF092632;
            case 2: return 0xFF3A1111;
            case 3: return 0xFF52647A;
            case 4: return 0xFF38234D;
            default: return 0xFF242424;
        }
    }

    private Drawable createPadBase(int padIndex) {
        int preset = preferences.getInt(KEY_PRESET, 0);
        if (preset == 0) return cloneDrawable(basePadBackgrounds[padIndex]);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(18));
        background.setColor(presetPadColor());
        return background;
    }

    private Drawable addPadBorder(int padIndex, Drawable base) {
        int color = getBorderColor(padIndex);
        if (color == 0) return base;
        return new LayerDrawable(new Drawable[]{base, createPadBorder(color)});
    }

    private Drawable createPadBorder(int color) {
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.RECTANGLE);
        border.setCornerRadius(dp(18));
        border.setColor(Color.TRANSPARENT);
        border.setStroke(dp(3), color);
        return border;
    }

    private void addBorderButton(LinearLayout row, String label, int preset, int[] selectedPad,
                                 CheckBox sameBorderForAll) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        int color = borderColorForPreset(preset);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                color == 0 ? 0xFF333333 : color));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1f));
        button.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();
            if (sameBorderForAll.isChecked()) {
                for (int i = 0; i < pads.length; i++) {
                    putBorderPreset(editor, i, preset);
                }
            } else {
                putBorderPreset(editor, selectedPad[0], preset);
            }
            editor.apply();
            for (int i = 0; i < pads.length; i++) applyPadImage(i);
        });
    }

    private void putBorderPreset(SharedPreferences.Editor editor, int padIndex, int preset) {
        editor.putInt(KEY_BORDER_PRESET + padIndex, preset)
                .putInt(KEY_BORDER_COLOR + padIndex, borderColorForPreset(preset));
    }

    private int getBorderPreset(int padIndex) {
        if (preferences.contains(KEY_BORDER_PRESET + padIndex)) {
            int preset = preferences.getInt(KEY_BORDER_PRESET + padIndex, BORDER_NONE);
            return preset >= BORDER_NONE && preset <= BORDER_RGB ? preset : BORDER_NONE;
        }
        int legacyColor = preferences.getInt(KEY_BORDER_COLOR + padIndex, 0);
        if (legacyColor == 0) return BORDER_NONE;
        if (legacyColor == 0xFFFF3333) return BORDER_RED;
        if (legacyColor == 0xFF168BFF) return BORDER_BLUE;
        if (legacyColor == 0xFFFFD600) return BORDER_YELLOW;
        return BORDER_NEON;
    }

    private int getBorderColor(int padIndex) {
        return borderColorForPreset(getBorderPreset(padIndex));
    }

    private int borderColorForPreset(int preset) {
        switch (preset) {
            case BORDER_NEON: return 0xFF39FF14;
            case BORDER_RED: return 0xFFFF3333;
            case BORDER_GREEN: return 0xFF00E676;
            case BORDER_YELLOW: return 0xFFFFD600;
            case BORDER_BLUE: return 0xFF168BFF;
            case BORDER_PURPLE: return 0xFFB04DFF;
            case BORDER_RGB: return 0xFFFF00FF;
            default: return 0;
        }
    }

    private void animateTouchBorder(int padIndex) {
        if (getBorderPreset(padIndex) == BORDER_NONE) return;
        Drawable background = pads[padIndex].getBackground();
        if (!(background instanceof LayerDrawable)) return;
        LayerDrawable layers = (LayerDrawable) background;
        Drawable lastLayer = layers.getDrawable(layers.getNumberOfLayers() - 1);
        if (!(lastLayer instanceof GradientDrawable)) return;
        GradientDrawable border = (GradientDrawable) lastLayer;
        int preset = getBorderPreset(padIndex);
        ValueAnimator animation;
        if (preset == BORDER_RGB) {
            int[] colors = {0xFFFF3333, 0xFFFFD600, 0xFF00E676, 0xFF168BFF,
                    0xFFB04DFF, 0xFFFF00FF};
            animation = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(),
                    colors[0], colors[1], colors[2], colors[3], colors[4], colors[5]);
            animation.setRepeatCount(1);
            animation.setRepeatMode(ValueAnimator.REVERSE);
        } else {
            int color = borderColorForPreset(preset);
            int dimColor = (color & 0x00FFFFFF) | 0x66000000;
            animation = ValueAnimator.ofObject(new android.animation.ArgbEvaluator(),
                    color, dimColor, color);
        }
        animation.setDuration(240);
        animation.addUpdateListener(valueAnimator ->
                border.setStroke(dp(3), (Integer) valueAnimator.getAnimatedValue()));
        animation.start();
    }

    private void clearCombinedImage() {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(KEY_BACKGROUND_IMAGE)
                .remove(KEY_COMBINED_IMAGE);
        for (int i = 0; i < pads.length; i++) {
            editor.remove(KEY_PAD_IMAGE + i);
        }
        editor.apply();
        for (int i = 0; i < pads.length; i++) applyPadImage(i);
        applyGlobalBackground();
        applyTogglePhoto();
        Toast.makeText(activity, "Combined photo removed from pads, background, and toggles",
                Toast.LENGTH_SHORT).show();
    }

    private void updateSelectedPadButtons(Button[] buttons, int selectedPad) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBackgroundResource(i == selectedPad
                    ? R.drawable.btn_3d_orange : R.drawable.btn_3d_dark);
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private interface ProgressAction {
        void onProgressChanged(int progress);
    }
}
