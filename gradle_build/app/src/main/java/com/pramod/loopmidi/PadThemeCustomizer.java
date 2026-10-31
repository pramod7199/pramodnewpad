package com.pramod.loopmidi;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
    private static final String KEY_PRESET = "theme_preset";
    private static final String KEY_BORDER_ALL = "theme_border_all";
    private static final String KEY_BORDER_COLOR = "theme_border_color_";

    private static final String TAG = "PadThemeCustomizer";
    private static final String KEY_PAD_IMAGE = "theme_pad_image_";
    private static final String KEY_PAD_OPACITY = "theme_pad_opacity_";
    private static final String KEY_BACKGROUND_IMAGE = "theme_background_image";
    private static final String KEY_BACKGROUND_OPACITY = "theme_background_opacity";
    private static final String KEY_ANIMATION_MODE = "theme_animation_mode";
    private static final int MODE_IMAGE_PULSE = 0;
    private static final int MODE_SHADOW_PULSE = 1;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Button[] pads;
    private final Drawable[] basePadBackgrounds;
    private final View root;
    private final View[] backgroundSurfaces;
    private final Drawable[] baseSurfaceBackgrounds;
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

    }

    void applySavedTheme() {
        for (int i = 0; i < pads.length; i++) {
            applyPadImage(i);
        }
        applyGlobalBackground();
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
        String[] presetNames = {"Default grey-black", "Neon", "Red", "Light combo"};
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

        CheckBox sameBorderForAll = new CheckBox(activity);
        sameBorderForAll.setText("Apply border color to all pads");
        sameBorderForAll.setChecked(preferences.getBoolean(KEY_BORDER_ALL, true));
        content.addView(sameBorderForAll);
        addSectionTitle(content, "PAD BORDER COLOR");
        LinearLayout borderColors = new LinearLayout(activity);
        borderColors.setOrientation(LinearLayout.HORIZONTAL);
        addBorderButton(borderColors, "Red", 0xFFFF3333, selectedPad, sameBorderForAll);
        addBorderButton(borderColors, "Blue", 0xFF168BFF, selectedPad, sameBorderForAll);
        addBorderButton(borderColors, "Yellow", 0xFFFFD600, selectedPad, sameBorderForAll);
        addBorderButton(borderColors, "None", 0, selectedPad, sameBorderForAll);
        content.addView(borderColors);
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
        return requestCode == REQUEST_PAD_IMAGE || requestCode == REQUEST_BACKGROUND_IMAGE;
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
            } else {
                preferences.edit().putString(KEY_BACKGROUND_IMAGE, uri.toString()).apply();
                applyGlobalBackground();
            }
        } catch (Exception error) {
            Log.e(TAG, "Could not apply selected theme image", error);
            Toast.makeText(activity, "Could not use that image: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    void onPadHit(int padIndex, View pad) {
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
        if (layers.getNumberOfLayers() < 2
                || !(layers.getDrawable(1) instanceof BitmapDrawable)) return;
        BitmapDrawable image = (BitmapDrawable) layers.getDrawable(1);
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
            int borderColor = preferences.getInt(KEY_BORDER_COLOR + padIndex, 0);
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
            preferences.edit().remove(KEY_BACKGROUND_IMAGE).apply();
            root.setBackgroundColor(presetRootColor());
            applyPresetSurfaceBackgrounds();
            Toast.makeText(activity, "Saved background image is unavailable",
                    Toast.LENGTH_LONG).show();
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
            default: return 0xFF111111;
        }
    }

    private int presetPadColor() {
        switch (preferences.getInt(KEY_PRESET, 0)) {
            case 1: return 0xFF092632;
            case 2: return 0xFF3A1111;
            case 3: return 0xFF52647A;
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
        int color = preferences.getInt(KEY_BORDER_COLOR + padIndex, 0);
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

    private void addBorderButton(LinearLayout row, String label, int color, int[] selectedPad,
                                 CheckBox sameBorderForAll) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                color == 0 ? 0xFF333333 : color));
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1f));
        button.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();
            if (sameBorderForAll.isChecked()) {
                for (int i = 0; i < pads.length; i++) {
                    editor.putInt(KEY_BORDER_COLOR + i, color);
                }
            } else {
                editor.putInt(KEY_BORDER_COLOR + selectedPad[0], color);
            }
            editor.apply();
            for (int i = 0; i < pads.length; i++) applyPadImage(i);
        });
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
