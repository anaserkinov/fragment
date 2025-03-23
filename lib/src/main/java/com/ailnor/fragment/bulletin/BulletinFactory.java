package com.ailnor.fragment.bulletin;

import static com.ailnor.core.UtilsKt.dp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.annotation.CheckResult;

import com.ailnor.fragment.Fragment;
import com.ailnor.fragment.R;


public final class BulletinFactory {

    public static BulletinFactory of(Fragment fragment) {
        return new BulletinFactory(fragment);
    }

    public static BulletinFactory of(FrameLayout layout) {
        return new BulletinFactory(layout);
    }

    public static boolean canShowBulletin(Fragment fragment) {
        return fragment != null && fragment.getParentActivity() != null && fragment.getLayoutContainer() != null;
    }

    public static final int ICON_TYPE_NOT_FOUND = 0;
    public static final int ICON_TYPE_WARNING = 1;

    private final Fragment fragment;
    private final FrameLayout containerLayout;

    private BulletinFactory(Fragment fragment) {
        this.fragment = fragment;
        this.containerLayout = null;
    }

    private BulletinFactory(FrameLayout containerLayout) {
        this.containerLayout = containerLayout;
        this.fragment = null;
    }

    public Bulletin createSimpleBulletin(int iconRawId, String text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
        layout.setAnimation(iconRawId, dp(36), dp(36));
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence subtext) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext());
        layout.setAnimation(iconRawId, dp(36), dp(36));
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtext);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence button, Runnable onButtonClick) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
        layout.setAnimation(iconRawId, dp(36), dp(36));
//        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, dp(14));
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        layout.textView.setText(text);
        layout.setButton(new Bulletin.UndoButton(getContext(), true).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(Drawable drawable, CharSequence text, String button, Runnable onButtonClick) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
        layout.imageView.setImageDrawable(drawable);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        layout.setButton(new Bulletin.UndoButton(getContext(), true).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    @CheckResult
    public Bulletin createCopyBulletin(String message) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
        layout.setAnimation(R.raw.copy, dp(36), dp(36), "NULL ROTATION", "Back", "Front");
        layout.textView.setText(message);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createErrorBulletin(CharSequence errorMessage) {
        Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
        layout.setAnimation(R.raw.chats_infotip);
        layout.textView.setText(errorMessage);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_SHORT);
    }
    private Bulletin create(Bulletin.Layout layout, int duration) {
        if (fragment != null) {
            return Bulletin.make(fragment, layout, duration);
        } else {
            return Bulletin.make(containerLayout, layout, duration);
        }
    }

    private Context getContext() {
        return fragment != null ? fragment.getParentActivity() : containerLayout.getContext();
    }
}
