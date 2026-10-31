package com.pramod.loopmidi;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
    private final Drawable baseRootBackground;
    private final View root;
    private int pickerPadIndex = -1;

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
        baseRootBackground = root.getBackground();
    }

    void applySavedTheme() {
        for (int i = 0; i < pads.length; i++) {
            applyPadImage(i);
        }
        applyGlobalBackground();
    }

    void showDialog(int padIndex) {
        pickerPadIndex = padIndex;
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);

        addSectionTitle(content, "PAD " + (padIndex + 1) + " IMAGE");
        Button choosePadImage = addButton(content, "Choose image for PAD " + (padIndex + 1));
        choosePadImage.setOnClickListener(v -> launchPicker(REQUEST_PAD_IMAGE));
        Button clearPadImage = addButton(content, "Remove pad image");
        clearPadImage.setOnClickListener(v -> {
            preferences.edit().remove(KEY_PAD_IMAGE + padIndex).apply();
            applyPadImage(padIndex);
            Toast.makeText(activity, "Pad image removed", Toast.LENGTH_SHORT).show();
        });
        addOpacityControl(content, "Pad image opacity",
                preferences.getInt(KEY_PAD_OPACITY + padIndex, 70),
                progress -> setPadOpacity(padIndex, progress));

        addSectionTitle(content, "GLOBAL LAYOUT BACKGROUND");
        Button chooseBackground = addButton(content, "Choose layout background image");
        chooseBackground.setOnClickListener(v -> launchPicker(REQUEST_BACKGROUND_IMAGE));
        Button clearBackground = addButton(content, "Remove background image");
        clearBackground.setOnClickListener(v -> {
            preferences.edit().remove(KEY_BACKGROUND_IMAGE).apply();
            applyGlobalBackground();
            Toast.makeText(activity, "Background image removed", Toast.LENGTH_SHORT).show();
        });
        addOpacityControl(content, "Background transparency",
                preferences.getInt(KEY_BACKGROUND_OPACITY, 70),
                this::setBackgroundOpacity);

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
            activity.getContentResolver().takePersistableUriPermission(uri, flags);
            if (requestCode == REQUEST_PAD_IMAGE) {
                if (pickerPadIndex < 0 || pickerPadIndex >= pads.length) {
                    throw new IOException("No pad selected for the image");
                }
                preferences.edit().putString(KEY_PAD_IMAGE + pickerPadIndex, uri.toString()).apply();
                applyPadImage(pickerPadIndex);
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
        String uriText = preferences.getString(KEY_PAD_IMAGE + padIndex, null);
        if (uriText == null) {
            pads[padIndex].setBackground(cloneDrawable(basePadBackgrounds[padIndex]));
            return;
        }
        try {
            Bitmap bitmap = decodeImage(Uri.parse(uriText), 1024);
            BitmapDrawable image = new BitmapDrawable(activity.getResources(), bitmap);
            image.setGravity(Gravity.FILL);
            image.setAlpha(preferences.getInt(KEY_PAD_OPACITY + padIndex, 70) * 255 / 100);
            Drawable base = cloneDrawable(basePadBackgrounds[padIndex]);
            LayerDrawable themedBackground = new LayerDrawable(new Drawable[]{base, image});
            pads[padIndex].setBackground(themedBackground);
        } catch (Exception error) {
            Log.e(TAG, "Could not load pad theme image for pad " + (padIndex + 1), error);
            preferences.edit().remove(KEY_PAD_IMAGE + padIndex).apply();
            pads[padIndex].setBackground(cloneDrawable(basePadBackgrounds[padIndex]));
            Toast.makeText(activity, "Saved image for PAD " + (padIndex + 1)
                    + " is unavailable", Toast.LENGTH_LONG).show();
        }
    }

    private void applyGlobalBackground() {
        String uriText = preferences.getString(KEY_BACKGROUND_IMAGE, null);
        if (uriText == null) {
            root.setBackground(cloneDrawable(baseRootBackground));
            return;
        }
        try {
            Bitmap bitmap = decodeImage(Uri.parse(uriText), 1600);
            BitmapDrawable image = new BitmapDrawable(activity.getResources(), bitmap);
            image.setGravity(Gravity.FILL);
            image.setAlpha(preferences.getInt(KEY_BACKGROUND_OPACITY, 70) * 255 / 100);
            root.setBackground(new LayerDrawable(new Drawable[]{
                    cloneDrawable(baseRootBackground), image
            }));
        } catch (Exception error) {
            Log.e(TAG, "Could not load global theme background", error);
            preferences.edit().remove(KEY_BACKGROUND_IMAGE).apply();
            root.setBackground(cloneDrawable(baseRootBackground));
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

    private void addOpacityControl(LinearLayout content, String label, int current,
                                   ProgressAction action) {
        TextView title = new TextView(activity);
        title.setText(label + ": " + clamp(current) + "%");
        content.addView(title);
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
