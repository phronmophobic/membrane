#include "skia.h"

#ifdef SK_GL

#if defined(__APPLE__)
#include <OpenGL/gl.h>
#else

#include <GL/gl.h>

#endif


#endif

#include "modules/skparagraph/include/FontCollection.h"
#include "modules/skparagraph/include/TypefaceFontProvider.h"
#include <iostream>
#include <fstream>

#include "src/gpu/gl/GrGLDefines.h"
#include "gl/GrGLInterface.h"

#include "src/core/SkFontPriv.h"
#include "src/core/SkStrikeSpec.h"
#include "src/utils/SkUTF.h"

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

class SimpleFontCollection : public skia::textlayout::FontCollection {
public:
    SimpleFontCollection(sk_sp<SkTypeface> typeface)
        : fFontProvider(sk_make_sp<skia::textlayout::TypefaceFontProvider>()) 
{

        fFontProvider->registerTypeface(typeface);

        // if (testOnly) {
        //     this->setTestFontManager(std::move(fFontProvider));
        // } else {
        //     this->setAssetFontManager(std::move(fFontProvider));
        // }
        // this->disableFontFallback();
        this->setDefaultFontManager(std::move(fFontProvider));
    }

    ~SimpleFontCollection() = default;


private:
    sk_sp<skia::textlayout::TypefaceFontProvider> fFontProvider;
};


// void log(std::string s){
//     std::ofstream myfile;
//     myfile.open ("/var/tmp/skia.log",  std::ios::out | std::ios::app);
//     myfile << s;
//     myfile.close();
// }


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
        sk_sp<SkSurface> rasterSurface =
            SkSurface::MakeRasterN32Premul(width, height);
        return new SkiaResource(nullptr, rasterSurface);
    }

    void skia_reshape(SkiaResource* resource, int frameBufferWidth, int frameBufferHeight, float xscale, float yscale){

        #ifdef SK_GL
        if ( resource->surface){
            resource->surface.reset();
            resource->grContext.reset();
        }

        sk_sp<GrDirectContext> grContext = GrDirectContext::MakeGL();
        SkASSERT(grContext);
        grContext->ref();

        GrGLFramebufferInfo info;
        info.fFBOID = (GrGLuint) 0;
        info.fFormat = GL_RGBA8;
        SkColorType colorType  = kRGBA_8888_SkColorType;

        int kStencilBits = 8;  // Skia needs 8 stencil bits
        int kMsaaSampleCount = 0;

        GrBackendRenderTarget target(frameBufferWidth, frameBufferHeight, kMsaaSampleCount, kStencilBits, info);

        SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);
        sk_sp<SkSurface> surface(SkSurface::MakeFromBackendRenderTarget(grContext.get(), target,
                                                                        kBottomLeft_GrSurfaceOrigin,
                                                                        colorType, nullptr, &surfaceProps));
        
        SkASSERT(surface);

        SkCanvas* canvas = surface->getCanvas();
        
        canvas->scale(xscale, yscale);

        resource->grContext = grContext;
        resource->surface = surface;

        #endif

    }

    void skia_clear(SkiaResource* resource){
        SkCanvas* canvas = resource->surface->getCanvas();
        canvas->clear(SK_ColorWHITE);
        // canvas->save();

        // canvas->save();
        // canvas->clipRect(SkRect::MakeWH(100, 100));
        // canvas->clear(SK_ColorRED);
        // canvas->scale(.5, .5);
        // canvas->clipRect(SkRect::MakeWH(100, 100));
        // canvas->clear(SK_ColorBLUE);
        // canvas->restore();

        // SkPaint paint;
        // paint.setColor(SK_ColorBLACK);

        // auto text = SkTextBlob::MakeFromString("Hello, Skia!", SkFont(nullptr, 18));
        // canvas->drawTextBlob(text.get(), 100, 150, paint);
        
        // canvas->save();
        // canvas->translate(100,150);
        // SkFont font(nullptr, 18);
        // skia_render_text(resource,&font , "hello there", 0, 0);
        // canvas->restore();


        // canvas->restore();

    }

    void skia_flush(SkiaResource* resource){
        resource->surface->getCanvas()->flush();
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
        SkAutoToGlyphs atg(*font, text, text_length, SkTextEncoding::kUTF8);
        const int glyphCount = atg.count();
        const SkGlyphID* glyphIDs = atg.glyphs();

        SkStrikeSpec strikeSpec = SkStrikeSpec::MakeCanonicalized(*font);
        SkBulkGlyphMetrics metrics{strikeSpec};
        SkSpan<const SkGlyph*> glyphs = metrics.glyphs(SkMakeSpan(glyphIDs, glyphCount));

        if ( glyphCount ){
            return glyphs[0]->advanceX();
        }else{
            return 0;
        }

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


        sk_sp<SkSurface> cpuSurface(SkSurface::MakeRaster(info));

        if (!cpuSurface) {
            SkDebugf("SkSurface::MakeRenderTarget returned null\n");
        }

        SkiaResource* bufResource = new SkiaResource(NULL, cpuSurface);

        return bufResource;
    }

    SkiaResource* skia_direct_bgra8888_buffer(void* buf, int width, int height, int rowBytes ) {
        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);

        sk_sp<SkSurface> surface =
            SkSurface::MakeRasterDirect(info, buf, rowBytes);

        SkiaResource* bufResource = new SkiaResource(NULL, surface);

        return bufResource;
    }

    void skia_browser_draw(SkiaResource* resource, const void* buffer, int width, int height){

        SkImageInfo info = SkImageInfo::Make(width, height, kBGRA_8888_SkColorType, kUnpremul_SkAlphaType);
        SkPixmap pixmap(info, buffer, info.width() * info.bytesPerPixel());
        resource->surface->writePixels(pixmap, 0, 0);
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

    // void skia_text_bounds2(SkFont* font, const char* text, int text_length, float* ox, float* oy, float* width, float* height){

    //         sk_sp<ResourceFontCollection> fontCollection = sk_make_sp<ResourceFontCollection>();
    //         sk_sp<ResourceFontCollection> fontCollection = sk_make_sp<ResourceFontCollection>();
    //         if (!fontCollection->fontsFound()) return;
    //         const char* text = "Hello World Text Dialog";
    //         const size_t len = strlen(text);

    //         ParagraphStyle paragraph_style;
    //         paragraph_style.turnHintingOff();
    //         ParagraphBuilderImpl builder(paragraph_style, fontCollection);

    //         TextStyle text_style;
    //         text_style.setFontFamilies({SkString("Roboto")});
    //         text_style.setColor(SK_ColorBLACK);
    //         builder.pushStyle(text_style);
    //         builder.addText(text, len);
    //         builder.pop();

    //         auto paragraph = builder.Build();
    //         paragraph->layout(200);
    //         paragraph->paint(canvas.get(), 0, 0);

    // }
    
    void skia_render_cursor(SkiaResource* resource, SkFont * font, const char* text, int text_length , int cursor){
        SkAutoToGlyphs atg(*font, text, text_length, SkTextEncoding::kUTF8);
        const int glyphCount = atg.count();
        const SkGlyphID* glyphIDs = atg.glyphs();

        SkStrikeSpec strikeSpec = SkStrikeSpec::MakeCanonicalized(*font, nullptr);
        SkBulkGlyphMetrics metrics{strikeSpec};
        SkSpan<const SkGlyph*> glyphs = metrics.glyphs(SkMakeSpan(glyphIDs, glyphCount));

        float x = 0;
        float startX = 0;
        float endX = 0;
        int index = 0;
        for( ; ; index ++ ) {

            if ( index == cursor ){
                startX = x;


                if ( index >= glyphs.size() ){
                    endX = x + font->measureText("8",1, SkTextEncoding::kUTF8);

                }else{
                    endX = x + glyphs[index]->advanceX();
                }
                break;
            }

            const SkGlyph* glyph = glyphs[index];
            x += glyph->advanceX();
        }

        SkRect rect = SkRect::MakeXYWH(startX, 0, endX - startX, font->getSpacing() + 1);
        resource->surface->getCanvas()->drawRect(rect, resource->getPaint());
    }

    void skia_render_selection(SkiaResource* resource, SkFont * font, const char* text, int text_length , int selection_start, int selection_end){
        SkAutoToGlyphs atg(*font, text, text_length, SkTextEncoding::kUTF8);
        const int glyphCount = atg.count();
        if (glyphCount == 0) {
            return;
        }
        const SkGlyphID* glyphIDs = atg.glyphs();

        SkStrikeSpec strikeSpec = SkStrikeSpec::MakeCanonicalized(*font, nullptr);
        SkBulkGlyphMetrics metrics{strikeSpec};
        SkSpan<const SkGlyph*> glyphs = metrics.glyphs(SkMakeSpan(glyphIDs, glyphCount));

        float x = 0;
        float startX = 0;
        float endX = 0;
        // int textSize = strlen(text);
        int index = 0;
        for( ; ; index ++ ) {

            if ( index == selection_start ){
                startX = x;
            }
            if (index == selection_end ){
                endX = x;
                break;
            }

            if ( index >= glyphs.size() ){
                endX = x;
                break;
            }

            const SkGlyph* glyph = glyphs[index];
            x += glyph->advanceX();
        }

        SkRect rect = SkRect::MakeXYWH(startX, 0, endX - startX, font->getSpacing());
        resource->surface->getCanvas()->drawRect(rect, resource->getPaint());

    }

    //https://developer.apple.com/fonts/TrueType-Reference-Manual/
    int skia_index_for_position(SkFont* font, const char* text, int text_length, float px){

        SkAutoToGlyphs atg(*font, text, text_length, SkTextEncoding::kUTF8);
        const int glyphCount = atg.count();
        if (glyphCount == 0) {
            return 0;
        }
        const SkGlyphID* glyphIDs = atg.glyphs();

        SkStrikeSpec strikeSpec = SkStrikeSpec::MakeCanonicalized(*font, nullptr);
        SkBulkGlyphMetrics metrics{strikeSpec};
        SkSpan<const SkGlyph*> glyphs = metrics.glyphs(SkMakeSpan(glyphIDs, glyphCount));

        float x = 0;
        // int textSize = strlen(text);
        int index = 0;
        for( ; ; index ++ ) {

            if ( index >= glyphs.size() ){
                return index;
            }

            const SkGlyph* glyph = glyphs[index];
            x += glyph->advanceX();
            if ( x > px){
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

    SkFont* skia_load_font2(const char* name, float size, int weight, int width, int slant){
        if ( name ){
            sk_sp<SkTypeface> typeface = SkTypeface::MakeFromFile(name);
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
                sk_sp<SkTypeface> typeface = SkTypeface::MakeFromName(name, style);

                if ( typeface ){
                    return new SkFont(typeface, size);
                } else {
                    return NULL;
                }
            }
        } else{
            return new SkFont(nullptr, size);
        }
    }


    SkImage* skia_load_image(const char* path){
        sk_sp<SkImage> image = SkImage::MakeFromEncoded(SkData::MakeFromFileName(path));
        if ( image ) {
            image->ref();
        }

        return image.get();

    }
    SkImage* skia_load_image_from_memory(const unsigned char *const buffer,int buffer_length){

        sk_sp<SkData> data = SkData::MakeWithCopy(buffer, buffer_length);

        sk_sp<SkImage> image = SkImage::MakeFromEncoded(data);
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


        sk_sp<SkSurface> cpuSurface(SkSurface::MakeRaster(info));

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
        switch (format){
        case 1  : fmt = SkEncodedImageFormat::kBMP  ; break;
        case 2  : fmt = SkEncodedImageFormat::kGIF  ; break;
        case 3  : fmt = SkEncodedImageFormat::kICO  ; break;
        case 4  : fmt = SkEncodedImageFormat::kJPEG ; break;
        case 5  : fmt = SkEncodedImageFormat::kPNG  ; break;
        case 6  : fmt = SkEncodedImageFormat::kWBMP ; break;
        case 7  : fmt = SkEncodedImageFormat::kWEBP ; break;
        case 8  : fmt = SkEncodedImageFormat::kPKM  ; break;
        case 9  : fmt = SkEncodedImageFormat::kKTX  ; break;
        case 10 : fmt = SkEncodedImageFormat::kASTC ; break;
        case 11 : fmt = SkEncodedImageFormat::kDNG  ; break;
        case 12 : fmt = SkEncodedImageFormat::kHEIF ; break;
        }

        sk_sp<SkData> img_data(img->encodeToData(fmt, quality));

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

#if defined(__APPLE__)
    void skia_osx_run_on_main_thread_sync(void(*callback)(void)){
        dispatch_sync(dispatch_get_main_queue(), ^{
                callback();
            });
    }
#endif
}
