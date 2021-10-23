package com.phronemophobic.membrane;
import com.sun.jna.*;

public class Skia {
            
    public static native Pointer skia_init();
    public static native Pointer skia_init_cpu(int width, int height);

    public static native void skia_reshape(Pointer resource, int frameBufferWidth, int frameBufferHeight, float xscale, float yscale);
    public static native void skia_clear(Pointer resources);
    public static native void skia_flush(Pointer resources);
    public static native void skia_cleanup(Pointer resources);
    public static native void skia_set_scale (Pointer resource, float sx, float sy);
    public static native void skia_render_line(Pointer resource, Pointer font, Pointer text, int text_length, float x, float y);
    public static native void skia_next_line(Pointer resource, Pointer font);
    public static native float skia_line_height(Pointer font);
    public static native float skia_advance_x(Pointer font, Pointer text, int text_length);
    public static native void skia_render_cursor(Pointer resource, Pointer font, Pointer text, int text_length , int cursor);
    public static native void skia_render_selection(Pointer resource, Pointer font, Pointer text, int text_length , int selection_start, int selection_end);

    public static native int skia_index_for_position(Pointer font, Pointer text, int text_length, float px);
    public static native void skia_text_bounds(Pointer font, Pointer text, int text_length, Pointer ox, Pointer oy, Pointer width, Pointer height);

    public static native void skia_save(Pointer resource);
    public static native void skia_restore(Pointer resource);
    public static native void skia_translate(Pointer resource, float tx, float ty);
    public static native void skia_rotate(Pointer resource, float degrees);
    public static native void skia_transform(Pointer resource, float scaleX, float skewX, float transX, float skewY, float scaleY, float transY);

    public static native void skia_clip_rect(Pointer resource, float ox, float oy, float width, float height);

    public static native Pointer skia_load_image(String path);
    public static native Pointer skia_load_image_from_memory(byte[] buf,int buffer_length);
    public static native void skia_draw_image(Pointer resource, Pointer image);
    public static native void skia_draw_image_rect(Pointer resource, Pointer image, float w, float h);

    public static native void skia_draw_path(Pointer resource, Pointer points, int count);
    public static native void skia_draw_polygon(Pointer resource, Pointer points, int count);

    public static native void skia_draw_rounded_rect(Pointer resource, float width, float height, float radius);

    public static native Pointer skia_load_font2(String name, float size, int weight, int width, int slant);


    // Paint related calls
    public static native void skia_push_paint(Pointer resource);
    public static native void skia_pop_paint(Pointer resource);
    public static native void skia_set_color(Pointer resource, float r, float g, float b, float a);
    public static native void skia_set_style(Pointer resource, byte style);
    public static native void skia_set_stroke_width(Pointer resource, float stroke_width);
    public static native void skia_set_alpha(Pointer resource, byte a);

    // offscreen buffer stuff
    public static native Pointer skia_offscreen_buffer(Pointer resource, int width, int height);
    public static native Pointer skia_offscreen_image(Pointer resource);

    public static native int skia_save_image(Pointer image, int format, int quality, String path);

    public static native int skia_fork_pty(short rows, short columns);

    static {
        Native.register("membraneskia");
    }

    public static void main(String[] args) {
        System.out.println("init: " + skia_init());
    }


}
