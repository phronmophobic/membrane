#include "skia.h"


#if defined(__APPLE__)
#include <OpenGL/gl.h>
#else

#include <GL/gl.h>


#endif



#include "modules/skparagraph/include/Paragraph.h"
#include "modules/skparagraph/include/ParagraphBuilder.h"

#include "modules/skparagraph/include/FontCollection.h"
#include "modules/skparagraph/include/TypefaceFontProvider.h"
#include <SkEncodedImageFormat.h>
#include <SkColorSpace.h>
#include <SkFontMgr.h>
#include <include/ports/SkFontMgr_empty.h>
#include <include/encode/SkPngEncoder.h>
#include <include/encode/SkJpegEncoder.h>
#include <include/encode/SkWebpEncoder.h>
#include "modules/svg/include/SkSVGDOM.h"
#include "modules/svg/include/SkSVGSVG.h"
#include "modules/svg/include/SkSVGRenderContext.h"
#include <SkRRect.h>
#include <iostream>
#include <fstream>

#include "include/gpu/ganesh/gl/GrGLDirectContext.h"
#include "include/gpu/ganesh/gl/GrGLBackendSurface.h"
#include "include/gpu/ganesh/GrBackendSurface.h"
#include "include/gpu/ganesh/GrDirectContext.h"
#include "include/gpu/ganesh/gl/GrGLInterface.h"
#include "include/gpu/ganesh/SkSurfaceGanesh.h"


// FONT STUFF //
// https://github.com/kyamagu/skia-python/commit/fa88b2febb5462844ef4d2e5c27e132d0f4594d2

#ifdef __APPLE__
#include "include/ports/SkFontMgr_mac_ct.h"
#endif

#ifdef __linux__
#include "include/ports/SkFontMgr_fontconfig.h"
#endif

#ifdef _WIN32
#include "include/ports/SkTypeface_win.h"
#endif

#include <mutex>

namespace {

bool g_factory_called = false;

}  // namespace

static sk_sp<SkFontMgr> fontmgr_factory() {
#if defined(__APPLE__)
  return SkFontMgr_New_CoreText(nullptr);
#elif defined(__linux__)
  return SkFontMgr_New_FontConfig(NULL);
#elif defined(_WIN32)
  return SkFontMgr_New_DirectWrite();
#else
  return SkFontMgr_New_Custom_Empty(); /* last resort: SkFontMgr::RefEmpty(); */
#endif
}

sk_sp<SkFontMgr> SkFontMgr_RefDefault() {
  static std::once_flag flag;
  static sk_sp<SkFontMgr> mgr;
  std::call_once(flag, [] {
    mgr = fontmgr_factory();
    g_factory_called = true;
  });
  return mgr;
}



// END FONT STUFF //

#if defined(__APPLE__)
#import <CoreFoundation/CoreFoundation.h>
#endif

#define LOG(fmt, ...) \
            do {FILE *fp; fp = fopen("/var/tmp/membrane.log", "a");fprintf(fp, fmt, __VA_ARGS__); fclose(fp);} while (0)

typedef struct _cef_rect_t {
  int x;
  int y;
  int width;
  int height;
} cef_rect_t;

using namespace skia::textlayout;


extern "C" {

    SkiaResource* skia_init(){

        // auto interface = GrGLMakeNativeInterface();

        // sk_sp<GrContext> grContext(GrContext::MakeGL(interface));
        // SkASSERT(grContext);

        // grContext->ref();

        // GrGLint buffer = 0;

        // GrGLFramebufferInfo info;
        // info.fFBOID = (GrGLuint) buffer;
        // SkColorType colorType;    
        // info.fFormat = GL_RGBA8;
        // colorType = kRGBA_8888_SkColorType;

        // // If you want multisampling, uncomment the below lines and set a sample count
        // static const int kStencilBits = 8;  // Skia needs 8 stencil bits
        // static const int kMsaaSampleCount = 0; //4;


        // GrBackendRenderTarget target(width*2, height*2, kMsaaSampleCount, kStencilBits, info);

        // // setup SkSurface
        // // To use distance field text, use commented out SkSurfaceProps instead
        // // SkSurfaceProps props(SkSurfaceProps::kUseDeviceIndependentFonts_Flag,
        // //                      SkSurfaceProps::kLegacyFontHost_InitType);
        // SkSurfaceProps props(SkSurfaceProps::kLegacyFontHost_InitType);

        // sk_sp<SkSurface> surface(SkSurface::MakeFromBackendRenderTarget(grContext.get(), target,
        //                                                                 kBottomLeft_GrSurfaceOrigin,
        //                                                                 colorType, nullptr, &props));
        // surface->ref();

        // SkCanvas* canvas = surface->getCanvas();
        // canvas->scale(2.0f, 2.0f);

        // resource = 

        return new SkiaResource(nullptr, nullptr);

    }

    SkiaResource* skia_init_cpu(int width, int height){
	sk_sp<SkSurface> rasterSurface(SkSurfaces::Raster(SkImageInfo::MakeN32Premul(width, height)));
        return new SkiaResource(nullptr, rasterSurface);
    }

    void skia_reshape(SkiaResource* resource, int frameBufferWidth, int frameBufferHeight, float xscale, float yscale){

        if ( resource->surface){
            resource->surface.reset();
            resource->grContext.reset();
        }

	// https://skia.org/docs/user/api/skcanvas_creation/#gpu

	// You've already created your OpenGL context and bound it.
	sk_sp<const GrGLInterface> interface = GrGLMakeNativeInterface();

	sk_sp<GrDirectContext> context = GrDirectContexts::MakeGL(interface);

	GrGLFramebufferInfo framebufferInfo;
        framebufferInfo.fFBOID = 0;
	framebufferInfo.fFormat = GL_RGBA8;
	auto backendRenderTarget = GrBackendRenderTargets::MakeGL(frameBufferWidth, frameBufferHeight, 0, 0 , framebufferInfo);

	sk_sp<SkSurface> gpuSurface(
	    SkSurfaces::WrapBackendRenderTarget(context.get(),
						backendRenderTarget,
						kBottomLeft_GrSurfaceOrigin,
						kRGBA_8888_SkColorType,
						nullptr,
						nullptr));
	if (!gpuSurface) {
	    return;
	}

	SkCanvas* gpuCanvas = gpuSurface->getCanvas();
	
	resource->grContext = context;
        
        gpuCanvas->scale(xscale, yscale);
	resource->surface = gpuSurface;
    }

    void skia_clear(SkiaResource* resource){
        SkCanvas* canvas = resource->surface->getCanvas();
        canvas->clear(SK_ColorWHITE);
    }

    void skia_flush_and_submit(SkiaResource* resource){
	resource->grContext->flush(resource->surface.get());
	resource->grContext->submit();
    }

    void skia_cleanup(SkiaResource* resource){
        delete resource;
    }

    void skia_set_scale (SkiaResource* resource, float sx, float sy){
        resource->surface->getCanvas()->scale(sx, sy);
    }
    // Should maybe paragraph stuff. See SkParagraphTest.cpp
    // does not currently support kerning see SkTypeface::getKerningPairAdjustments() and https://skia.org/user/tips#kerning
    void skia_render_line(SkiaResource* resource, SkFont* font, const char* text, int text_length, float x, float y){


        SkCanvas* canvas = resource->surface->getCanvas();
        canvas->drawSimpleText(text, text_length , SkTextEncoding::kUTF8, x,y ,*font, resource->getPaint());

    }

    void skia_next_line(SkiaResource* resource, SkFont* font){
        resource->surface->getCanvas()->translate(0, font->getSpacing());
    }

    float skia_line_height(SkFont* font){
        return font->getSpacing();
    }

    void skia_font_metrics(SkFont* font,
                           uint32_t *fFlags,
                           SkScalar *fTop,
                           SkScalar *fAscent,
                           SkScalar *fDescent,
                           SkScalar *fBottom,
                           SkScalar *fLeading,
                           SkScalar *fAvgCharWidth,
                           SkScalar *fMaxCharWidth,
                           SkScalar *fXMin,
                           SkScalar *fXMax,
                           SkScalar *fXHeight,
                           SkScalar *fCapHeight,
                           SkScalar *fUnderlineThickness,
                           SkScalar *fUnderlinePosition,
                           SkScalar *fStrikeoutThickness,
                           SkScalar *fStrikeoutPosition){

        SkFontMetrics metrics;
        font->getMetrics(&metrics);

        *fFlags= metrics.fFlags;
        *fTop= metrics.fTop;
        *fAscent= metrics.fAscent;
        *fDescent= metrics.fDescent;
        *fBottom= metrics.fBottom;
        *fLeading= metrics.fLeading;
        *fAvgCharWidth= metrics.fAvgCharWidth;
        *fMaxCharWidth= metrics.fMaxCharWidth;
        *fXMin= metrics.fXMin;
        *fXMax= metrics.fXMax;
        *fXHeight= metrics.fXHeight;
        *fCapHeight= metrics.fCapHeight;
        *fUnderlineThickness= metrics.fUnderlineThickness;
        *fUnderlinePosition= metrics.fUnderlinePosition;
        *fStrikeoutThickness= metrics.fStrikeoutThickness;
        *fStrikeoutPosition= metrics.fStrikeoutPosition;
    }

    float skia_advance_x(SkFont* font, const char* text, int text_length){
        return font->measureText(text, text_length, SkTextEncoding::kUTF8, NULL);
    }

    void skia_text_bounds(SkFont* font, const char* text, int text_length, float* ox, float* oy, float* width, float* height){
        *ox = 0;
        *oy = 0;
        *width = 0;
        *height = 0;

        int start = 0;
        int end = 0;
        float y = 0;
        SkRect bounds;
        for ( ; end < text_length; end++){
            if ( text[end] == '\n' ){
                y += font->getSpacing();

                font->measureText(text+start,end - start,  SkTextEncoding::kUTF8, &bounds);

                float x0 = bounds.x();
                float x1 = bounds.x() + bounds.width();
                float y0 = y;
                float y1 = y0 + font->getSpacing();

                *ox = std::min(std::min(*ox, x0), x1);
                *oy = std::min(std::min(*oy, y0), y1);

                *width = std::max(std::max(*width, x0), x1);
                *height = std::max(std::max(*height, y0), y1);
                start = end + 1;
            }
        }
        if ( start != end){
            font->measureText(text+start,end - start,  SkTextEncoding::kUTF8, &bounds);

            float x0 = bounds.x();
            float x1 = bounds.x() + bounds.width();
            float y0 = y;
            float y1 = y0 + font->getSpacing();

            *ox = std::min(std::min(*ox, x0), x1);
            *oy = std::min(std::min(*oy, y0), y1);

            *width = std::max(std::max(*width, x0), x1);
            *height = std::max(std::max(*height, y0), y1);
        }

    }

    SkiaResource* skia_browser_buffer(int width, int height){

        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);


	sk_sp<SkSurface> cpuSurface(SkSurfaces::Raster(info));

        if (!cpuSurface) {
            SkDebugf("SkSurface::MakeRenderTarget returned null\n");
        }

        SkiaResource* bufResource = new SkiaResource(NULL, cpuSurface);

        return bufResource;
    }

    SkiaResource* skia_direct_bgra8888_buffer(void* buf, int width, int height, int rowBytes ) {
        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);

        sk_sp<SkSurface> surface =
            SkSurfaces::WrapPixels(info, buf, rowBytes);

        SkiaResource* bufResource = new SkiaResource(NULL, surface);

        return bufResource;
    }

    void skia_browser_draw(SkiaResource* resource, const void* buffer, int width, int height){

        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
        SkPixmap pixmap(info, buffer, info.width() * info.bytesPerPixel());
        resource->surface->writePixels(pixmap, 0, 0);
    }

    void skia_draw_pixmap(SkiaResource* resource, SkColorType colorType, SkAlphaType alphaType,   void* buffer, int width, int height, int rowBytes){

        SkImageInfo info = SkImageInfo::Make(width, height, colorType, alphaType);

        sk_sp<SkSurface> sourceSurface =
            SkSurfaces::WrapPixels(info, buffer, rowBytes);

        sourceSurface->draw(resource->surface->getCanvas(), 0, 0, &resource->getPaint());
    }

    void skia_bgra8888_draw(SkiaResource* resource, const void* buffer, int width, int height, int rowBytes){

        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
        SkPixmap pixmap(info, buffer, rowBytes);
        resource->surface->writePixels(pixmap, 0, 0);
    }

    void skia_browser_update(SkiaResource* resource,int dirtyRectsCount, cef_rect_t const* dirtyRects, const void* buffer, int width, int height){

        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
        SkPixmap pixmap(info, buffer, info.width() * info.bytesPerPixel());

        for (int i = 0; i < dirtyRectsCount; i ++){

            const cef_rect_t& rect = dirtyRects[i];
            SkPixmap dirtyPixmap;
            if(pixmap.extractSubset(&dirtyPixmap, {rect.x, rect.y, rect.x+rect.width, rect.y+rect.height})){
                resource->surface->writePixels(dirtyPixmap, rect.x, rect.y);
            }
        }
    }

    void skia_draw_surface(SkiaResource* destinationResource, SkiaResource* sourceResource){
        sourceResource->surface->draw(destinationResource->surface->getCanvas(), 0, 0, &destinationResource->getPaint());
    }

    
    void skia_render_cursor(SkiaResource* resource, SkFont * font, const char* text, int text_length , int cursor){
        int glyphCount = font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, NULL, 0);
        std::vector<SkGlyphID> glyphs(glyphCount);
        font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, glyphs.data(), glyphs.size());

        std::vector<SkScalar> xposs(glyphCount);
        font->getXPos(glyphs.data(), glyphs.size(), xposs.data());

        std::vector<SkScalar> widths(glyphCount);
        font->getWidths(glyphs.data(), glyphs.size(), widths.data());

        float startX;
        float endX;
        
        if ( cursor < glyphCount ){
            startX = xposs[cursor];
            endX = startX + widths[cursor];
        } else {
            if ( xposs.empty() ){
                startX = 0;
            } else {
                startX = xposs.back() + widths.back();
            }
            endX = startX + font->measureText("8",1, SkTextEncoding::kUTF8);
        }
        SkRect rect = SkRect::MakeXYWH(startX, 0, endX - startX, font->getSpacing());
        resource->surface->getCanvas()->drawRect(rect, resource->getPaint());
    }

    void skia_render_selection(SkiaResource* resource, SkFont * font, const char* text, int text_length , int selection_start, int selection_end){
        if ( selection_start == selection_end){ return; }

        int glyphCount = font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, NULL, 0);
        std::vector<SkGlyphID> glyphs(glyphCount);
        font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, glyphs.data(), glyphs.size());

        if (glyphCount == 0){ return; }

        std::vector<SkScalar> xposs(glyphCount);
        font->getXPos(glyphs.data(), glyphs.size(), xposs.data());

        std::vector<SkScalar> widths(glyphCount);
        font->getWidths(glyphs.data(), glyphs.size(), widths.data());
        
        float startX = xposs[selection_start];
        int endIndex = std::min(std::min(selection_end, (int)widths.size() - 1),
                                (int)xposs.size() - 1);
        float endX = xposs[endIndex] + widths[endIndex];

        SkRect rect = SkRect::MakeXYWH(startX, 0, endX - startX, font->getSpacing());
        resource->surface->getCanvas()->drawRect(rect, resource->getPaint());

    }

    //https://developer.apple.com/fonts/TrueType-Reference-Manual/
    int skia_index_for_position(SkFont* font, const char* text, int text_length, float px){
        int glyphCount = font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, NULL, 0);
        std::vector<SkGlyphID> glyphs(glyphCount);
        font->textToGlyphs(text, text_length, SkTextEncoding::kUTF8, glyphs.data(), glyphs.size());

        std::vector<SkScalar> xposs(glyphCount);
        font->getXPos(glyphs.data(), glyphs.size(), xposs.data());

        std::vector<SkScalar> widths(glyphCount);
        font->getWidths(glyphs.data(), glyphs.size(), widths.data());

        float x = 0;
        int index = 0;
        for( ; ; index ++ ) {

            if ( index >= glyphCount ){
                return index;
            }

            float glyphEndX = xposs[index] + widths[index];
            if ( glyphEndX > px){
                break;
            }
        }

        return index;
    }

    void skia_save(SkiaResource* resource){
        resource->surface->getCanvas()->save();
    }

    void skia_restore(SkiaResource* resource){
        resource->surface->getCanvas()->restore();
    }

    void skia_translate(SkiaResource* resource, float tx, float ty){
        resource->surface->getCanvas()->translate(tx, ty);
    }

    void skia_rotate(SkiaResource* resource, float degrees){
        resource->surface->getCanvas()->rotate(degrees);
    }

    void skia_transform(SkiaResource* resource, 
                        float  	scaleX,
                        float  	skewX,
                        float  	transX,
                        float  	skewY,
                        float  	scaleY,
                        float  	transY
    ){
        SkMatrix matrix;
        // float affine[] = {scaleX, skewX, transX, skewY, scaleY, transY};

        // column major order
        float affine[] = {scaleX, skewY, skewX, scaleY, transX, transY};
        matrix.setAffine(affine);
        resource->surface->getCanvas()->concat(matrix);
    }

    void skia_clip_rect(SkiaResource* resource, float ox, float oy, float width, float height){
        resource->surface->getCanvas()->clipRect(SkRect::MakeXYWH(ox, oy, width, height));
    }

    void skia_font_family_name(SkFont* font, char* familyName, size_t len){
        SkString s = SkString();
        font->getTypeface()->getFamilyName(&s);
        strncpy(familyName, s.c_str(), len);
    }

    SkFontStyle* skia_FontStyle_make(int weight, int width, int slant){
        if ( width == -1 ){ width = SkFontStyle::kNormal_Width; }
        if ( weight == -1 ){ weight = SkFontStyle::kNormal_Weight; }

        SkFontStyle::Slant skslant;
        switch ( slant ){
        case 2:
            skslant = SkFontStyle::kItalic_Slant;
            break;
        case 3:
            skslant = SkFontStyle::kOblique_Slant;
            break;
        case -1:
        case 1:
        default:
            skslant = SkFontStyle::kUpright_Slant;
            break;
        }

        SkFontStyle* style = new SkFontStyle(weight, width, skslant);
        return style;
    }

    void skia_FontStyle_delete(SkFontStyle* style){
        delete style;
    }

    SkFont* skia_load_font2(const char* name, float size, int weight, int width, int slant){
        if ( name ){
	    sk_sp<SkTypeface> typeface = SkFontMgr_RefDefault()->makeFromFile(name);
            if ( typeface ){
                SkFont* font = new SkFont(typeface, size);

                return font;

            } else {
                if ( width == -1 ){ width = SkFontStyle::kNormal_Width; }
                if ( weight == -1 ){ weight = SkFontStyle::kNormal_Weight; }

                SkFontStyle::Slant skslant;
                switch ( slant ){
                case 2:
                    skslant = SkFontStyle::kItalic_Slant;
                    break;
                case 3:
                    skslant = SkFontStyle::kOblique_Slant;
                    break;
                case -1:
                case 1:
                default:
                    skslant = SkFontStyle::kUpright_Slant;
                    break;
                }

                SkFontStyle style(weight, width, skslant);
                sk_sp<SkTypeface> typeface = SkFontMgr_RefDefault()->legacyMakeTypeface(name, style);

                if ( typeface ){
                    return new SkFont(typeface, size);
                } else {
                    return NULL;
                }
            }
        } else{
            return new SkFont(SkFontMgr_RefDefault()->matchFamilyStyle(NULL, SkFontStyle()), size);
        }
    }


    SkImage* skia_load_image(const char* path){
        sk_sp<SkImage> image = SkImages::DeferredFromEncodedData(SkData::MakeFromFileName(path));
        if ( image ) {
            image->ref();
        }

        return image.get();

    }
    SkImage* skia_load_image_from_memory(const unsigned char *const buffer,int buffer_length){

        sk_sp<SkData> data = SkData::MakeWithCopy(buffer, buffer_length);

        sk_sp<SkImage> image = SkImages::DeferredFromEncodedData(data);
        if ( image ) {
            image->ref();
        }

        return image.get();
    }

    void skia_draw_image(SkiaResource* resource, SkImage* image){
        resource->surface->getCanvas()->drawImage(image, 0, 0);
    }

    void skia_draw_image_rect(SkiaResource* resource, SkImage* image, float w, float h){
        resource->surface->getCanvas()->drawImageRect(image, SkRect::MakeXYWH(0.f, 0.f, w, h), SkSamplingOptions(), &resource->getPaint());
    }

    void skia_image_bounds(SkImage* image, int* width, int* height){
        *width = image->width();
        *height = image->height();
    }

    void skia_draw_path(SkiaResource* resource, float* points, int count){

        if ( count >= 2){
            SkPath path;
            path.moveTo(points[0], points[1]);

            for ( int i = 2; i < count; i += 2){
                path.lineTo(points[i], points[i + 1]);
            }

            resource->surface->getCanvas()->drawPath(path, resource->getPaint());

        }
    }

    void skia_draw_polygon(SkiaResource* resource, float* points, int count){
        if ( count >= 2){
            SkPath path;
            path.moveTo(points[0], points[1]);

            for ( int i = 2; i < count; i += 2){
                path.lineTo(points[i], points[i + 1]);
            }

            resource->surface->getCanvas()->drawPath(path, resource->getPaint());

        }
    
    }

    SkPath* skia_make_path(){
        return new SkPath();
    }

    void skia_delete_path(SkPath* path){
        delete path;
    }

    void skia_reset_path(SkPath* path){
        path->reset();
    }

    void skia_skpath_moveto(SkPath* path, double x, double y){
        path->moveTo(x, y);
    }

    void skia_skpath_lineto(SkPath* path, double x, double y){
        path->lineTo(x, y);
    }

    void skia_skpath_arcto(SkPath* path, double x1, double y1, double x2, double y2, double radius){
        path->arcTo(x1, y1, x2, y2, radius);
    }

    void skia_skpath_cubicto(SkPath* path, double x1, double y1, double x2, double y2, double x3, double y3){
        path->cubicTo(x1, y1, x2, y2, x3, y3);
    }

    void skia_skpath_conicto(SkPath* path, double x1, double y1, double x2, double y2, double w){
        path->conicTo(x1, y1, x2, y2, w);
    }

    void skia_skpath_close(SkPath* path){
        path->close();
    }

    void skia_skpath_draw(SkiaResource* resource, SkPath* path){
        resource->surface->getCanvas()->drawPath(*path, resource->getPaint());
    }

    void skia_draw_rounded_rect(SkiaResource* resource, float width, float height, float radius){
        SkRRect rrect = SkRRect::MakeRectXY({0, 0, width, height}, radius, radius);
        resource->surface->getCanvas()->drawRRect(rrect, resource->getPaint());
    }

    // works, but not sure about API
    // void skia_draw_rounded_rect_nine_patch(SkiaResource* resource, float width, float height, float leftRad, float topRad, float rightRad, float bottomRad){
    //     SkRRect rrect;
    //     rrect.setNinePatch({0, 0, width, height}, leftRad, topRad, rightRad, bottomRad);
    //     resource->surface->getCanvas()->drawRRect(rrect, resource->getPaint());
    // }

    void skia_push_paint(SkiaResource* resource){
        resource->pushPaint();
    }

    void skia_pop_paint(SkiaResource* resource){
        resource->popPaint();
    }

    void skia_set_color(SkiaResource* resource, float r, float g, float b, float a){
        resource->getPaint().setColor4f({r,g,b,a});
    }

    void skia_set_style(SkiaResource* resource, SkPaint::Style style){
        resource->getPaint().setStyle(style);
    }

    void skia_set_stroke_width(SkiaResource* resource, float stroke_width){
        resource->getPaint().setStrokeWidth(stroke_width);
    }

    void skia_set_alpha(SkiaResource* resource, unsigned char a){
        resource->getPaint().setAlpha(a);
    }

    SkiaResource* skia_offscreen_buffer(SkiaResource* resource, int width, int height){

        SkImageInfo info = SkImageInfo:: MakeN32Premul(width, height);


	sk_sp<SkSurface> cpuSurface(SkSurfaces::Raster(info));

        // gpu surface won't draw if offscreen originally
        // sk_sp<SkSurface> gpuSurface(
        //     SkSurface::MakeRenderTarget(resource->grContext.get(), SkBudgeted::kNo, info));
        if (!cpuSurface) {
            SkDebugf("SkSurface::MakeRenderTarget returned null\n");
        }

        SkiaResource* cpuResource = new SkiaResource(resource->grContext, cpuSurface);

        cpuResource->paints.pop();
        cpuResource->paints.emplace(SkPaint(resource->getPaint()));

        return cpuResource;
    }

    SkImage* skia_offscreen_image(SkiaResource* resource){
        sk_sp<SkImage> imgP(resource->surface->makeImageSnapshot());
        SkImage* img = imgP.get();
        img->ref();

        delete resource;
        return img;

    }

    int skia_save_image(SkiaResource* resource, int format, int quality, const char* path){

        sk_sp<SkImage> img(resource->surface->makeImageSnapshot());
        if (!img) { return 0; }

        SkEncodedImageFormat fmt = SkEncodedImageFormat::kPNG;
	sk_sp<SkData> img_data = NULL;
        switch (format){
        // case 1  : fmt = SkEncodedImageFormat::kBMP  ; break;
        // case 2  : fmt = SkEncodedImageFormat::kGIF  ; break;
        // case 3  : fmt = SkEncodedImageFormat::kICO  ; break;
        // case 4  : fmt = SkEncodedImageFormat::kJPEG ; break;
        // case 5  : fmt = SkEncodedImageFormat::kPNG  ; break;
        // case 6  : fmt = SkEncodedImageFormat::kWBMP ; break;
        // case 7  : fmt = SkEncodedImageFormat::kWEBP ; break;
        // case 8  : fmt = SkEncodedImageFormat::kPKM  ; break;
        // case 9  : fmt = SkEncodedImageFormat::kKTX  ; break;
        // case 10 : fmt = SkEncodedImageFormat::kASTC ; break;
        // case 11 : fmt = SkEncodedImageFormat::kDNG  ; break;
        // case 12 : fmt = SkEncodedImageFormat::kHEIF ; break;
        case 4: 
	{
	    auto options = SkJpegEncoder::Options();
	    options.fQuality = quality;
	    img_data = SkJpegEncoder::Encode(resource->grContext.get(), img.get(), options);
	    break;
	}
        case 5:
	    img_data = SkPngEncoder::Encode(resource->grContext.get(), img.get(), SkPngEncoder::Options());
	    break;
        case 7:
	{
	    auto options = SkWebpEncoder::Options();
	    options.fQuality = quality;
	    img_data = SkWebpEncoder::Encode(resource->grContext.get(), img.get(), options);
	    break;
	}


        }

        if (!img_data) { return 0; }
        SkFILEWStream out(path);
        return out.write(img_data->data(), img_data->size());

    }


    int skia_fork_pty(unsigned short rows, unsigned short columns){
        // struct winsize ws = {.ws_row = rows, .ws_col = columns};
        // int pt;
        // pid_t pid = forkpty(&pt, NULL, NULL, &ws);

        // if (pid < 0){
        //     return -1;
        // } else if (pid == 0){

        //     setsid();
        //     // setenv("MTM", buf, 1);
        //     setenv("TERM", DEFAULT_256_COLOR_TERMINAL
        //            , 1);
        //     // signal(SIGCHLD, SIG_DFL);
        //     execl("/bin/bash", "/bin/bash", NULL);
        //     return 0;
        // }

        // // fcntl(pt, F_SETFL, O_NONBLOCK);
        // return pt;

        return -1;

    }

    SkColor skia_SkColor4f_make(float red, float green, float blue, float alpha){
        SkColor4f color;
        color.fR = red;
        color.fG = green;
        color.fB = blue;
        color.fA = alpha;
        return color.toSkColor();
    }

    void skia_SkColor4f_getComponents(SkColor color, float* red, float* green, float* blue, float* alpha){
        SkColor4f colorf = SkColor4f::FromColor(color);
        *red = colorf.fR;
        *green = colorf.fG;
        *blue = colorf.fB;
        *alpha = colorf.fA;
    }

    void skia_SkRefCntBase_ref(SkRefCntBase* o){
        o->ref();
    }

    void skia_SkRefCntBase_unref(SkRefCntBase* o){
        o->unref();
    }

    void skia_ParagraphBuilder_delete(ParagraphBuilder* pb){
        delete pb;
    }

    ParagraphBuilder* skia_ParagraphBuilder_make(ParagraphStyle* paragraphStyle){

        auto fontCollection = sk_make_sp<FontCollection>();
        fontCollection->setDefaultFontManager(SkFontMgr_RefDefault());
        // fontCollection->enableFontFallback();

        ParagraphBuilder* pb = ParagraphBuilder::make(*paragraphStyle, fontCollection).release();
        return pb;
    }
    void skia_ParagraphBuilder_pushStyle(ParagraphBuilder *pb, TextStyle* style){
        pb->pushStyle(*style);
    }
    void skia_ParagraphBuilder_pop(ParagraphBuilder *pb){
        pb->pop();
    }
    void skia_ParagraphBuilder_addText(ParagraphBuilder *pb, char* text, int len){
        pb->addText(text, len);
    }

    void skia_ParagraphBuilder_addPlaceholder(ParagraphBuilder *pb, PlaceholderStyle* placeholderStyle){
        pb->addPlaceholder(*placeholderStyle);
    }

    // PlaceholderStyle(SkScalar width, SkScalar height, PlaceholderAlignment alignment,
    //                  TextBaseline baseline, SkScalar offset)
    void skia_ParagraphBuilder_addPlaceholder2(ParagraphBuilder *pb, float width, float height, int alignment, int baseline, float offset){
        PlaceholderStyle style(width, height, (PlaceholderAlignment)alignment, (TextBaseline)baseline, offset);
        pb->addPlaceholder(style);
    }


    void skia_Paragraph_delete(Paragraph* p){
        delete p;
    }

    Paragraph* skia_ParagraphBuilder_build(ParagraphBuilder *pb){
        return pb->Build().release();
    }
    void skia_ParagraphBuilder_reset(ParagraphBuilder *pb){
        pb->Reset();
    }

    void skia_TextStyle_delete(TextStyle* style){
        delete style;
    }

    TextStyle* skia_TextStyle_make(){
        return new TextStyle();
    }

    void skia_TextStyle_setColor(TextStyle* style, uint32_t color ){
        style->setColor(color);
    }
    void skia_TextStyle_setForeground(TextStyle* style, SkPaint* foregroundColor){
        style->setForegroundColor(*foregroundColor);
    }

    void skia_TextStyle_clearForegroundColor(TextStyle* style){
        style->clearForegroundColor();
    }
    void skia_TextStyle_setBackgroundColor(TextStyle* style, SkPaint* backgroundColor){
        style->setBackgroundColor(*backgroundColor);
    }
    void skia_TextStyle_clearBackgroundColor(TextStyle* style){
        style->clearBackgroundColor();
    }
    void skia_TextStyle_setDecoration(TextStyle* style, int decoration){
        style->setDecoration((TextDecoration)decoration);
    }

    void skia_TextStyle_setDecorationMode(TextStyle* style, int mode) {


        TextDecorationMode m;
        switch(m){
        case 0: m = kGaps; break;
        case 1: m = kThrough; break;
        }

        style->setDecorationMode(m);
    }
    void skia_TextStyle_setDecorationStyle(TextStyle* style, int tdStyle) {
        TextDecorationStyle s;
        switch(tdStyle){
        case 0: s = kSolid; break;
        case 1: s =  kDouble; break;
        case 2: s = kDotted ; break;
        case 3: s = kDashed; break;
        case 4: s = kWavy; break;
        }

        style->setDecorationStyle(s);
    }
    void skia_TextStyle_setDecorationColor(TextStyle* style, uint32_t color) {
        style->setDecorationColor(color);
    }
    void skia_TextStyle_setDecorationThicknessMultiplier(TextStyle* style, float m) {
        style->setDecorationThicknessMultiplier(m);
    }

    void skia_TextStyle_setFontStyle(TextStyle* style, SkFontStyle* fontStyle){
        style->setFontStyle(*fontStyle);
    }
    void skia_TextStyle_addShadow(TextStyle* style, TextShadow* shadow){
        style->addShadow(*shadow);
    }
    void skia_TextStyle_resetShadows(TextStyle* style){
        style->resetShadows();
    }
    void skia_TextStyle_setFontSize(TextStyle* style, float fontSize){
        style->setFontSize(fontSize);
    }
    void skia_TextStyle_setFontFamilies(TextStyle* style, SkString** familiesArr, int familiesCount){
        std::vector<SkString> families(familiesCount);
        for (int i = 0; i < familiesCount; ++i) {
            families[i] = *familiesArr[i];
        }

        style->setFontFamilies(families);
    }
    void skia_TextStyle_setBaselineShift(TextStyle* style, float shift){
        style->setBaselineShift(shift);
    }
    void skia_TextStyle_setHeight(TextStyle* style, float height){
        style->setHeight(height);
    }
    void skia_TextStyle_setHeightOverride(TextStyle* style, int heightOverride){
        style->setHeightOverride(heightOverride);
    }
    void skia_TextStyle_setHalfLeading(TextStyle* style, int halfLeading){
        style->setHalfLeading(halfLeading);
    }
    void skia_TextStyle_setLetterSpacing(TextStyle* style, float letterSpacing){
        style->setLetterSpacing(letterSpacing);
    }
    void skia_TextStyle_setWordSpacing(TextStyle* style, float wordSpacing){
        style->setWordSpacing(wordSpacing);
    }
    void skia_TextStyle_setTypeface(TextStyle* style, SkTypeface* typeface){
        style->setTypeface(sk_ref_sp(typeface));
    }
    void skia_TextStyle_setLocale(TextStyle* style, SkString* locale){
        style->setLocale(*locale);
    }
    void skia_TextStyle_setTextBaseline(TextStyle* style, int baseline){
        TextBaseline tb;
        if (baseline == 1){
            tb = skia::textlayout::TextBaseline::kIdeographic;
        }else{
            tb = skia::textlayout::TextBaseline::kAlphabetic;
        }
        style->setTextBaseline(tb);
    }
    void skia_TextStyle_setPlaceholder(TextStyle* style){
        style->setPlaceholder();
    }

    void skia_ParagraphStyle_delete(ParagraphStyle* ps){
        delete ps;
    }

    ParagraphStyle* skia_ParagraphStyle_make(){
        return new ParagraphStyle();
    }

    void skia_ParagraphStyle_turnHintingOff(ParagraphStyle* paragraphStyle){
        paragraphStyle->turnHintingOff();
    }

    void skia_ParagraphStyle_setStrutStyle(ParagraphStyle* paragraphStyle, StrutStyle* strutStyle){
        paragraphStyle->setStrutStyle(*strutStyle);
    }

    void skia_ParagraphStyle_setTextStyle(ParagraphStyle* paragraphStyle, TextStyle* textStyle){
        paragraphStyle->setTextStyle(*textStyle);
    }

    void skia_ParagraphStyle_setTextDirection(ParagraphStyle* paragraphStyle, int direction){

        TextDirection d;
        switch (direction){
        case 0: d = TextDirection::kRtl;break;
        case 1: d = TextDirection::kLtr;break;
        }
        paragraphStyle->setTextDirection(d);
    }

    void skia_ParagraphStyle_setTextAlign(ParagraphStyle* paragraphStyle, int align){
        TextAlign a;

        switch(align){
        case 0:
            a = TextAlign::kLeft; break;
        case 1:
            a = TextAlign::kRight; break;
        case 2:
            a = TextAlign::kCenter; break;
        case 3:
            a = TextAlign::kJustify; break;
        case 4:
            a = TextAlign::kStart; break;
        case 5:
            a = TextAlign::kEnd; break;
        }

        paragraphStyle->setTextAlign(a);
    }

    void skia_ParagraphStyle_setMaxLines(ParagraphStyle* paragraphStyle, int maxLines){
        paragraphStyle->setMaxLines(maxLines);
    }

    void skia_ParagraphStyle_setEllipsis(ParagraphStyle* paragraphStyle, SkString* ellipsis){
        paragraphStyle->setEllipsis(*ellipsis);
    }

    void skia_ParagraphStyle_setHeight(ParagraphStyle* paragraphStyle, float height){
        paragraphStyle->setHeight(height);
    }

    void skia_ParagraphStyle_setTextHeightBehavior(ParagraphStyle* paragraphStyle, int v){
        TextHeightBehavior thb;
        switch (v){

        case 0:
            v = kAll; break;
        case 1:
            v = kDisableFirstAscent; break;
        case 2:
            v = kDisableLastDescent; break;
        case 0x1 | 0x2:
            v = kDisableAll; break;
        }
        paragraphStyle->setTextHeightBehavior(thb);
    }

    void skia_ParagraphStyle_setReplaceTabCharacters(ParagraphStyle* paragraphStyle, int value){
        paragraphStyle->setReplaceTabCharacters(value);
    }


    SkString* skia_SkString_make_utf8(char *s, int len){
        return new SkString(s, len);
    }
    void skia_SkString_delete(SkString* s){
        delete s;
    }

    // ;; SkScalar getMaxWidth() { return fWidth; }
    float skia_Paragraph_getMaxWidth(Paragraph* para){
        return para->getMaxWidth();
    }
    // ;; SkScalar getHeight() { return fHeight; }
    float skia_Paragraph_getHeight(Paragraph* para){
        return para->getHeight();
    }
    // ;; SkScalar getMinIntrinsicWidth() { return fMinIntrinsicWidth; }
    float skia_Paragraph_getMinIntrinsicWidth(Paragraph* para){
        return para->getMinIntrinsicWidth();
    }
    // ;; SkScalar getMaxIntrinsicWidth() { return fMaxIntrinsicWidth; }
    float skia_Paragraph_getMaxIntrinsicWidth(Paragraph* para){
        return para->getMaxIntrinsicWidth();
    }
    // ;; SkScalar getAlphabeticBaseline() { return fAlphabeticBaseline; }
    float skia_Paragraph_getAlphabeticBaseline(Paragraph* para){
        return para->getAlphabeticBaseline();
    }
    // ;; SkScalar getIdeographicBaseline() { return fIdeographicBaseline; }
    float skia_Paragraph_getIdeographicBaseline(Paragraph* para){
        return para->getIdeographicBaseline();
    }
    // ;; SkScalar getLongestLine() { return fLongestLine; }
    float skia_Paragraph_getLongestLine(Paragraph* para){
        return para->getLongestLine();
    }
    // ;; bool didExceedMaxLines() { return fExceededMaxLines; }
    int skia_Paragraph_didExceedMaxLines(Paragraph* para){
        return para->didExceedMaxLines();
    }
    // ;; virtual void layout(SkScalar width) = 0;
    void skia_Paragraph_layout(Paragraph* para, float width){
        return para->layout(width);
    }
    // ;; virtual void paint(SkCanvas* canvas, SkScalar x, SkScalar y) = 0;
    void skia_Paragraph_paint(Paragraph* para, SkiaResource* resource, float x, float y){
        SkCanvas* canvas = resource->surface->getCanvas();
        return para->paint(canvas, x, y);
    }
    // ;; virtual void paint(ParagraphPainter* painter, SkScalar x, SkScalar y) = 0;

    // ;; // Returns a vector of bounding boxes that enclose all text between
    // ;; // start and end glyph indexes, including start and excluding end
    // ;; virtual std::vector<TextBox> getRectsForRange(unsigned start,
    // ;;                                               unsigned end,
    // ;;                                               RectHeightStyle rectHeightStyle,
    // ;;                                               RectWidthStyle rectWidthStyle) = 0;
//    skia_Paragraph_getRectsForRange(Paragraph* para);
    int skia_Paragraph_getRectsForRange(Paragraph* para, int start, int end, int rectHeightStyle, int rectWidthStyle, float* buf, int max){
        auto boxes = para->getRectsForRange(start, end, (RectHeightStyle)rectHeightStyle, (RectWidthStyle)rectWidthStyle);
        int cnt = std::min(boxes.size(), (size_t)max);
        for( int i = 0; i < cnt; i++){
            TextBox tb(boxes[i]);
            SkRect rect(tb.rect);
            buf[i*4+0] = rect.x();
            buf[i*4+1] = rect.y();
            buf[i*4+2] = rect.width();
            buf[i*4+3] = rect.height();
            
        }

        return cnt;
        
    }


// struct TextBox {
//     SkRect rect;
//     TextDirection direction;

//     TextBox(SkRect r, TextDirection d) : rect(r), direction(d) {}
// };
    // ;; virtual std::vector<TextBox> getRectsForPlaceholders() = 0;
    int skia_Paragraph_getRectsForPlaceholders(Paragraph* para, float* buf, int max){
        
        auto boxes = para->getRectsForPlaceholders();
        int cnt = std::min(boxes.size(), (size_t)max);
        for( int i = 0; i < cnt; i++){
            TextBox tb(boxes[i]);
            SkRect rect(tb.rect);
            buf[i*4+0] = rect.x();
            buf[i*4+1] = rect.y();
            buf[i*4+2] = rect.width();
            buf[i*4+3] = rect.height();
            
        }

        return cnt;
    }

//    skia_Paragraph_getRectsForPlaceHolders(Paragraph* para);
    // ;; // Returns the index of the glyph that corresponds to the provided coordinate,
    // ;; // with the top left corner as the origin, and +y direction as down
    // ;; virtual PositionWithAffinity getGlyphPositionAtCoordinate(SkScalar dx, SkScalar dy) = 0;
    void skia_Paragraph_getGlyphPositionAtCoordinate(Paragraph* para, float dx, float dy, int* pos, int* affinity){
        PositionWithAffinity pwa = para->getGlyphPositionAtCoordinate(dx, dy);

        *pos = pwa.position;
        if ( pwa.affinity == kUpstream ){
            *affinity = 0;
        }else {
            *affinity = 1;
        }
    }

//    skia_Paragraph_getGlyphPositionAtCoordinate(Paragraph* para);
    // ;; // Finds the first and last glyphs that define a word containing
    // ;; // the glyph at index offset
    // ;; virtual SkRange<size_t> getWordBoundary(unsigned offset) = 0;
//    skia_Paragraph_getWordBoundary(Paragraph* para);
    // ;; virtual void getLineMetrics(std::vector<LineMetrics>&) = 0;
//    skia_Paragraph_getLineMetrics(Paragraph* para);

    int skia_count_font_families(){
        return SkFontMgr_RefDefault()->countFamilies();
    }

    void skia_get_family_name(char* familyName, size_t len, int index){
        SkString s = SkString();
        SkFontMgr_RefDefault()->getFamilyName(index, &s);
        strncpy(familyName, s.c_str(), len);
    }

    SkStream* skia_SkStream_make_from_bytes(char* s, int len){
        return new SkMemoryStream(s, len, true);
    }

    SkStream* skia_SkStream_make_from_path(char* s){
        return new SkFILEStream(s);
    }

    void skia_SkStream_delete(SkStream* stream){
        delete stream;
    }

    SkSVGDOM* skia_SkSVGDOM_make(SkStream* stream){
        return SkSVGDOM::MakeFromStream(*stream).release();
    }

    void skia_SkSVGDOM_delete(SkSVGDOM* svg){
        svg->unref();
    }

    void skia_SkSVGDOM_render(SkSVGDOM* svg, SkiaResource* resource){
        svg->render(resource->surface->getCanvas());
    }

    void skia_SkSVGDOM_set_container_size(SkSVGDOM* svg, float width, float height){
        svg->setContainerSize(SkSize::Make(width, height));
    }

    void skia_SkSVGDOM_instrinsic_size(SkSVGDOM* svg, float *width, float *height){
        SkSize size = svg->getRoot()->intrinsicSize(SkSVGLengthContext(SkSize::Make(0, 0)));
        *width = size.width();
        *height = size.height();
    }


    /** SkPaint Wrappers **/

    SkPaint* skia_Paint_make(){
        return new SkPaint();
    }

    void skia_Paint_delete(SkPaint* paint){
        delete paint;
    }

    void skia_Paint_reset(SkPaint* paint){
        paint->reset();
    }

    int skia_Paint_isAntiAlias(SkPaint* paint)  {
        return paint->isAntiAlias();
    }

    void skia_Paint_setAntiAlias(SkPaint* paint, int aa) { paint->setAntiAlias(aa); }

    int skia_Paint_isDither(SkPaint* paint)  {
        return paint->isDither();
    }

    void skia_Paint_setDither(SkPaint* paint, int dither) { paint->setDither(dither); }

    void skia_Paint_setStroke(SkPaint* paint, int stroke){
        paint->setStroke(stroke);
    }

    uint32_t skia_Paint_getColor(SkPaint* paint)  { return paint->getColor(); }

    void skia_Paint_setColor(SkPaint* paint, uint32_t color){
        paint->setColor(color);
    }

    float skia_Paint_getAlphaf(SkPaint* paint)  { return paint->getAlphaf(); }

    // Helper that scales the alpha by 255.
    uint8_t skia_Paint_getAlpha(SkPaint* paint)  {
        return paint->getAlpha();
    }

    void skia_Paint_setAlphaf(SkPaint* paint, float a){
        paint->setAlphaf(a);
    }

    SkScalar skia_Paint_getStrokeWidth(SkPaint* paint)  { return paint->getStrokeWidth(); }

    void skia_Paint_setStrokeWidth(SkPaint* paint, SkScalar width){
        paint->setStrokeWidth(width);
    }

    SkScalar skia_Paint_getStrokeMiter(SkPaint* paint)  { return paint->getStrokeMiter(); }

    void skia_Paint_setStrokeMiter(SkPaint* paint, SkScalar miter){
        paint->setStrokeMiter(miter);
    }

    // enum Cap {
    //     kButt_Cap,                  //!< no stroke extension
    //     kRound_Cap,                 //!< adds circle
    //     kSquare_Cap,                //!< adds square
    //     kLast_Cap    = kSquare_Cap, //!< largest Cap value
    //     kDefault_Cap = kButt_Cap,   //!< equivalent to kButt_Cap
    // };

    // enum Join : uint8_t {
    //     kMiter_Join,                 //!< extends to miter limit
    //     kRound_Join,                 //!< adds circle
    //     kBevel_Join,                 //!< connects outside edges
    //     kLast_Join    = kBevel_Join, //!< equivalent to the largest value for Join
    //     kDefault_Join = kMiter_Join, //!< equivalent to kMiter_Join
    // };

    int getStrokeCap(SkPaint* paint)  { return paint->getStrokeCap(); }

    void skia_Paint_setStrokeCap(SkPaint* paint, int cap){
        paint->setStrokeCap((SkPaint::Cap)cap);
    }

    uint8_t skia_Paint_getStrokeJoin(SkPaint* paint)  {
        return paint->getStrokeJoin();
    }
    void skia_Paint_setStrokeJoin(SkPaint* paint, uint8_t join){
        paint->setStrokeJoin((SkPaint::Join)join);
    }

    int skia_Paint_getBlendMode_or(SkPaint* paint, int defaultMode) {
        return (int)paint->getBlendMode_or((SkBlendMode)defaultMode);
    };

    int skia_Paint_isSrcOver(SkPaint* paint) {
        return paint->isSrcOver();
    }

    void skia_Paint_setBlendMode(SkPaint* paint, int mode){
        paint->setBlendMode((SkBlendMode)mode);
    }

    /** END SkPaint Wrappers **/

#if defined(__APPLE__)
    void skia_osx_run_on_main_thread_sync(void(*callback)(void)){
        dispatch_sync(dispatch_get_main_queue(), ^{
                callback();
            });
    }
#endif
}
