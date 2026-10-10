package es.jesus.ampodcasts;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared OLED surfaces and compact visual controls with 48 dp touch targets. */
final class UiStyle {
    static int dp(Context c,int n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static int accent(Context c){return android.os.Build.VERSION.SDK_INT>=31 && c.getSharedPreferences("library",Context.MODE_PRIVATE).getBoolean("dynamicColors",true)?c.getColor(android.R.color.system_accent1_200):0xffa8c7fa;}
    static GradientDrawable surface(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static Drawable ripple(Context c,int inset){return new RippleDrawable(ColorStateList.valueOf(0x33a8c7fa),new InsetDrawable(surface(c,0xff181c24,14),dp(c,inset),dp(c,inset),dp(c,inset),dp(c,inset)),null);}
    static void button(Button b){Context c=b.getContext();b.setAllCaps(false);b.setTextSize(13);b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0xff747b86,accent(c)}));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));b.setBackground(ripple(c,4));}
    static void icon(ImageButton b,String kind,String description){Context c=b.getContext();b.setImageDrawable(new ControlIcon(kind,accent(c),dp(c,22)));b.setBackground(ripple(c,5));b.setPadding(dp(c,13),dp(c,13),dp(c,13),dp(c,13));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setMinimumWidth(dp(c,48));b.setMinimumHeight(dp(c,48));b.setContentDescription(description);b.setTooltipText(description);}
    static LinearLayout.LayoutParams spaced(Context c){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(c,4);p.bottomMargin=dp(c,4);return p;}
    static void toggle(Switch v){Context c=v.getContext();v.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));v.setBackground(surface(c,0xff111318,16));v.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{accent(c),0xffa6abb3}));v.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0xff3d536a,0xff343941}));v.setSwitchPadding(dp(c,16));}
    static ArrayAdapter<String> choices(Context c,String[] labels){return new ArrayAdapter<String>(c,android.R.layout.simple_spinner_item,labels){private View style(View view){TextView t=(TextView)view;t.setTextColor(0xfff2f2f2);t.setTextSize(14);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(c,14),dp(c,10),dp(c,32),dp(c,10));t.setMinHeight(dp(c,48));t.setBackgroundColor(0xff181c24);return view;}public View getView(int i,View v,ViewGroup parent){TextView t=(TextView)style(super.getView(i,v,parent));t.setBackgroundColor(Color.TRANSPARENT);t.setCompoundDrawablesWithIntrinsicBounds(null,null,new ControlIcon("expand",accent(c),dp(c,20)),null);return t;}public View getDropDownView(int i,View v,ViewGroup parent){return style(super.getDropDownView(i,v,parent));}};}
    static void spinner(Spinner s){Context c=s.getContext();s.setBackground(ripple(c,0));s.setPopupBackgroundDrawable(surface(c,0xff181c24,16));}
}
