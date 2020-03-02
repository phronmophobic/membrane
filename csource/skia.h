#include <stack>
#include "GrContext.h"
#include "gl/GrGLInterface.h"
#include "SkData.h"
#include "SkImage.h"
#include "SkStream.h"
#include "SkSurface.h"

#include "include/gpu/GrBackendSurface.h"
#include "include/core/SkCanvas.h"
#include "include/core/SkFont.h"
#include "include/utils/SkRandom.h"
/* #include "src/gpu/gl/GrGLUtil.h" */
#include "src/gpu/gl/GrGLDefines.h"
#include "SkTextBlob.h"


class SkiaResource {



public:

    sk_sp<GrContext> grContext;
    sk_sp<SkSurface> surface;
    std::stack<SkPaint> paints;

    ~SkiaResource(){
        grContext.reset();
        surface.reset();
    }

    SkiaResource(sk_sp<GrContext> _grContext, sk_sp<SkSurface> _surface):grContext(_grContext), surface(_surface){
        paints.emplace(SkPaint());
        SkPaint& paint = paints.top();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(1);
        paint.setColor(SK_ColorBLACK);
    }

    SkPaint& getPaint(){
        return paints.top();
    }

    void pushPaint(){
        paints.emplace(SkPaint(paints.top()));
    }

    void popPaint(){
        paints.pop();
    }
};


extern "C"{
    SkiaResource* skia_init();
    SkiaResource* skia_init_cpu(int width, int height);

    void skia_reshape(SkiaResource* resource, int frameBufferWidth, int frameBufferHeight, float xscale, float yscale);
    void skia_clear(SkiaResource* resources);
    void skia_flush(SkiaResource* resources);
    void skia_cleanup(SkiaResource* resources);
    void skia_set_scale (SkiaResource* resource, float sx, float sy);
    void skia_render_line(SkiaResource* resource, SkFont* font, const char* text, int text_length, float x, float y);
    void skia_next_line(SkiaResource* resource, SkFont* font);
    float skia_line_height(SkFont* font);
    void skia_render_cursor(SkiaResource* resource, SkFont * font, const char* text, int text_length , int cursor);
    void skia_render_selection(SkiaResource* resource, SkFont * font, const char* text, int text_length , int selection_start, int selection_end);

    int skia_index_for_position(SkFont* font, const char* text, int text_length, float px);
    void skia_text_bounds(SkFont* font, const char* text, int text_length, float* ox, float* oy, float* width, float* height);

    void skia_save(SkiaResource* resource);
    void skia_restore(SkiaResource* resource);
    void skia_translate(SkiaResource* resource, float tx, float ty);
    void skia_clip_rect(SkiaResource* resource, float ox, float oy, float width, float height);

    SkImage* skia_load_image(const char* path);
    SkImage* skia_load_image_from_memory(const unsigned char *const buffer,int buffer_length);
    void skia_draw_image(SkiaResource* resource, SkImage* image);
    void skia_draw_image_rect(SkiaResource* resource, SkImage* image, float w, float h);

    void skia_draw_path(SkiaResource* resource, float* points, int count);
    void skia_draw_polygon(SkiaResource* resource, float* points, int count);

    void skia_draw_rounded_rect(SkiaResource* resource, float width, float height, float radius);

    SkFont* skia_load_font(const char* fontfilename, float fontsize);


    // Paint related calls
    void skia_push_paint(SkiaResource* resource);
    void skia_pop_paint(SkiaResource* resource);
    void skia_set_color(SkiaResource* resource, float r, float g, float b, float a);
    void skia_set_style(SkiaResource* resource, SkPaint::Style style);
    void skia_set_stroke_width(SkiaResource* resource, float stroke_width);
    void skia_set_alpha(SkiaResource* resource, unsigned char a);

    // offscreen buffer stuff
    SkiaResource* skia_offscreen_buffer(SkiaResource* resource, int width, int height);
    SkImage* skia_offscreen_image(SkiaResource* resource);

    int skia_save_image(SkiaResource* image, int format, int quality, const char* path);

#if defined(__APPLE__)
    void skia_osx_run_on_main_thread_sync(void(*callback)(void));
#endif
}
