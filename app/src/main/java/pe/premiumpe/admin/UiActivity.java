package pe.premiumpe.admin;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

abstract class UiActivity extends Activity {
    static final int BG = 0xfffaf5ef, PAPER = 0xfffffaf7, INK = 0xff391b15, MUTED = 0xff674e45;
    static final int AJI = 0xff993c24, RED = AJI, LINE = 0xffe4d3c8, SOFT = 0xfff4e5da;
    static final int GREEN = 0xff12783a, ROCOTO = 0xffa13020, GOLD = 0xffffd379;
    LinearLayout root;
    LinearLayout body;
    ScrollView scroll;

    int dp(float amount) { return Math.round(amount * getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (stroke != 0) background.setStroke(dp(1), stroke);
        return background;
    }
    void pad(View view, int left, int top, int right, int bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }
    void gap(LinearLayout into, int height) { into.addView(new View(this), 1, dp(height)); }
    void add(LinearLayout into, View child) { into.addView(child, -1, -2); }
    void weight(LinearLayout into, View child) {
        into.addView(child, new LinearLayout.LayoutParams(0, -2, 1f));
    }
    TextView text(String label, int size, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        if (strong) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    void heading(LinearLayout into, String label) { add(into, text(label, 17, INK, true)); gap(into, 8); }
    void note(LinearLayout into, String label) { add(into, text(label, 13, MUTED, false)); }
    TextView badge(String label, int bgColor, int textColor, int borderColor) {
        TextView view = text(label, 11, textColor, true);
        view.setBackground(rounded(bgColor, 8, borderColor));
        pad(view, 7, 3, 7, 3);
        return view;
    }
    TextView monogram(String letter, int sizeDp, int bgColor, int textColor) {
        TextView view = text(letter, sizeDp / 2, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(bgColor, Math.max(8, sizeDp / 3), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        view.setLayoutParams(lp);
        return view;
    }
    LinearLayout card(LinearLayout parent) {
        LinearLayout view = column();
        view.setBackground(rounded(PAPER, 20, LINE));
        pad(view, 16, 16, 16, 16);
        add(parent, view);
        gap(parent, 12);
        return view;
    }
    Button button(String label, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(primary ? PAPER : INK);
        button.setBackground(rounded(primary ? AJI : SOFT, 14, primary ? 0 : LINE));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }
    EditText input(LinearLayout into, String label, String value, int type) {
        TextView title = text(label, 14, INK, true);
        add(into, title);
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        title.setLabelFor(field.getId());
        field.setTextSize(16);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setInputType(type);
        if ((type & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) field.setSingleLine(true);
        field.setText(value);
        field.setBackground(rounded(PAPER, 12, LINE));
        field.setMinHeight(dp(48));
        pad(field, 12, 9, 12, 9);
        gap(into, 4);
        add(into, field);
        gap(into, 12);
        return field;
    }
    Spinner choices(LinearLayout into, String label, String[] options, int selected, Runnable changed) {
        TextView title = text(label, 13, MUTED, true);
        add(into, title);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        title.setLabelFor(spinner.getId());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected));
        spinner.setMinimumHeight(dp(48));
        add(into, spinner);
        if (changed != null) spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { changed.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        return spinner;
    }
    Switch toggle(LinearLayout into, String label, boolean value) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTextColor(INK);
        view.setMinHeight(dp(48));
        view.setChecked(value);
        add(into, view);
        return view;
    }
    void watch(EditText input, Runnable action) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int s, int c, int a) {}
            @Override public void onTextChanged(CharSequence value, int s, int before, int count) { action.run(); }
            @Override public void afterTextChanged(Editable value) {}
        });
    }
    void page(String title, String label) {
        root = column();
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bounds = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() |
                WindowInsets.Type.displayCutout());
            view.setPadding(bounds.left, bounds.top, bounds.right, bounds.bottom);
            return insets;
        });
        setContentView(root);
        LinearLayout header = column();
        pad(header, 18, 12, 18, 10);
        if (label != null) add(header, text(label, 13, RED, true));
        add(header, text(title, 29, INK, true));
        add(root, header);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = column();
        pad(body, 16, 6, 16, 18);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}