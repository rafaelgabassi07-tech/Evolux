package br.com.valorae.carteira.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import br.com.valorae.carteira.R;

public final class UiFactory {
    private UiFactory() {}

    public static MaterialCardView metricCard(Context ctx, String label, String value, String subtitle) {
        MaterialCardView card = baseCard(ctx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 18);
        content.setPadding(pad, pad, pad, pad);

        View accent = new View(ctx);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 4));
        accentParams.bottomMargin = dp(ctx, 14);
        accent.setLayoutParams(accentParams);
        accent.setBackgroundColor(ContextCompat.getColor(ctx, R.color.valorae_primary));

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label == null ? "" : label.toUpperCase());
        tvLabel.setTextSize(11);
        tvLabel.setLetterSpacing(0.08f);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvLabel.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_secondary));

        TextView tvValue = new TextView(ctx);
        tvValue.setText(value == null || value.isEmpty() ? "—" : value);
        tvValue.setTextSize(22);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_primary));
        tvValue.setPadding(0, dp(ctx, 7), 0, 0);

        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText(subtitle == null ? "" : subtitle);
        tvSubtitle.setTextSize(12);
        tvSubtitle.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_secondary));
        tvSubtitle.setPadding(0, dp(ctx, 5), 0, 0);

        content.addView(accent);
        content.addView(tvLabel);
        content.addView(tvValue);
        if (subtitle != null && !subtitle.isEmpty()) content.addView(tvSubtitle);
        card.addView(content);
        return card;
    }

    public static MaterialCardView rowCard(Context ctx, String title, String subtitle, String trailing, int trailingColor) {
        MaterialCardView card = baseCard(ctx);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textWrap = new LinearLayout(ctx);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title == null || title.isEmpty() ? "Informação" : title);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_primary));
        tvTitle.setLineSpacing(0, 1.08f);

        TextView tvSubtitle = new TextView(ctx);
        tvSubtitle.setText(subtitle == null ? "" : subtitle);
        tvSubtitle.setTextSize(12);
        tvSubtitle.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_secondary));
        tvSubtitle.setPadding(0, dp(ctx, 4), dp(ctx, 8), 0);
        tvSubtitle.setLineSpacing(0, 1.08f);

        textWrap.addView(tvTitle);
        if (subtitle != null && !subtitle.isEmpty()) textWrap.addView(tvSubtitle);

        if (trailing != null && !trailing.isEmpty()) {
            TextView tvTrailing = new TextView(ctx);
            tvTrailing.setText(trailing);
            tvTrailing.setTextSize(13);
            tvTrailing.setTypeface(Typeface.DEFAULT_BOLD);
            tvTrailing.setGravity(Gravity.CENTER);
            tvTrailing.setMinWidth(dp(ctx, 58));
            tvTrailing.setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7));
            tvTrailing.setTextColor(trailingColor != 0 ? trailingColor : ContextCompat.getColor(ctx, R.color.valorae_text_primary));
            tvTrailing.setBackgroundResource(R.drawable.bg_chip_soft);
            row.addView(textWrap);
            row.addView(tvTrailing);
        } else {
            row.addView(textWrap);
        }
        card.addView(row);
        return card;
    }

    public static MaterialCardView emptyState(Context ctx, String title, String message) {
        MaterialCardView card = baseCard(ctx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(ctx, 22), dp(ctx, 24), dp(ctx, 22), dp(ctx, 24));
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_primary));
        TextView tvMsg = new TextView(ctx);
        tvMsg.setText(message);
        tvMsg.setTextSize(13);
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setPadding(0, dp(ctx, 8), 0, 0);
        tvMsg.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_secondary));
        content.addView(tvTitle);
        content.addView(tvMsg);
        card.addView(content);
        return card;
    }

    public static TextView sectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.valorae_text_primary));
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(ctx, 8), 0, dp(ctx, 12));
        return tv;
    }

    public static MaterialCardView baseCard(Context ctx) {
        MaterialCardView card = new MaterialCardView(ctx);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(ctx, 12);
        card.setLayoutParams(params);
        card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.valorae_surface));
        card.setStrokeColor(ContextCompat.getColor(ctx, R.color.valorae_surface_variant));
        card.setStrokeWidth(dp(ctx, 1));
        card.setRadius(dp(ctx, 20));
        card.setCardElevation(dp(ctx, 1));
        return card;
    }

    public static View spacer(Context ctx, int dp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, dp)));
        return v;
    }

    public static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }
}
