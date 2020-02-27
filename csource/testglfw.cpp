#define GLFW_INCLUDE_GLCOREARB
#define GL_GLEXT_PROTOTYPES

#include <GLFW/glfw3.h>
#include <stdio.h>

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
#include "SkTypeface.h"
#include "skia.h"

static GLFWwindow *window = NULL;
static int swapAndPollInput();

double xpos = 0 ;
double ypos = 0;
static void cursor_position_callback(GLFWwindow* window, double _xpos, double _ypos)
{
    xpos = _xpos;
    ypos = _ypos;
}


void draw(SkiaResource* resource){

    SkCanvas* canvas = resource->surface->getCanvas();

    canvas->save();

    canvas->clipRect(SkRect::MakeWH(100, 100));
    canvas->clear(SK_ColorRED);
    canvas->scale(.5, .5);
    canvas->clipRect(SkRect::MakeWH(100, 100));
    canvas->clear(SK_ColorBLUE);


    canvas->restore();

    SkFont* menlo = skia_load_font("/System/Library/Fonts/Helvetica.ttc", 30);

    if (! menlo ){
      return;
    }


    // SkImageInfo info = SkImageInfo:: MakeN32Premul(800, 800);
    // sk_sp<SkSurface> gpuSurface(
    //     SkSurface::MakeRenderTarget(canvas->getGrContext(), SkBudgeted::kNo, info));
    // if (!gpuSurface) {
    //     SkDebugf("SkSurface::MakeRenderTarget returned null\n");
    // }
    // SkCanvas* gpuCanvas = gpuSurface->getCanvas();
    

    static SkImage* img = nullptr;

    if ( !img ){

        SkiaResource* gpuResource = skia_offscreen_buffer(resource, 800*2, 800*2);

        {
            SkCanvas* gpuCanvas = gpuResource->surface->getCanvas();
            gpuCanvas->scale(2,2);
            // gpuCanvas->clear(SK_ColorWHITE);
            
            char stext [] = "The quick brown fox jumped over the lazy dog";

            skia_render_line(gpuResource, menlo,  stext, strlen(stext), 0, 0);
            

            SkPaint rectpaint;
            rectpaint.setStyle(SkPaint::kFill_Style);
            rectpaint.setColor(SK_ColorBLACK);
            rectpaint.setAntiAlias(true);
            rectpaint.setStrokeWidth(1);
            rectpaint.setAlpha(128);

            gpuCanvas->drawRect(SkRect::MakeXYWH(0, 0, 50,50), rectpaint);}
        img = skia_offscreen_image(gpuResource);
    }    


    SkPaint paint;
    paint.setAntiAlias(true);
    
    canvas->save();
    canvas->translate(100,80);
    canvas->drawImageRect(img, SkRect::MakeXYWH(0, 0, 800, 800), &paint);
    
    canvas->restore();



    paint.setColor(SK_ColorBLACK);


    {
        const char* text[] = {"The quick brown",
                             "fox jumped over",
                             "the lzy dog",
                             "",
                             "dood."};

        canvas->save();

        canvas->translate(100,100);

        int index = skia_index_for_position(menlo, text[0], strlen(text[0]), xpos - 100);


        if ( index > 1 ){

            // skia_render_selection(resource, menlo, text[0], strlen(text[0]), 1, index);
            skia_push_paint(resource);

            skia_set_color(resource, 1, 0,0, 0.5f);
            skia_render_cursor(resource, menlo, text[0], strlen(text[0]), index);

            skia_pop_paint(resource);

        }

        canvas->save();
        for ( int i = 0; i < 5; i++){
            skia_render_line(resource, menlo, text[i], strlen(text[i]), 0, 0);
            skia_next_line(resource,menlo);
        }
        canvas->restore();

        
        canvas->translate(10,300);
        skia_render_cursor(resource, menlo, "", 0, 0);




        canvas->restore();

        /* auto textblob = SkTextBlob::MakeFromString(text, *menlo); */
        /* canvas->drawTextBlob(textblob.get(), 50, 25, paint); */
        /* canvas->drawString(text, 100, 100, *menlo, paint); */

        
    }

    canvas->save();
    {
        SkPaint rectpaint;
        rectpaint.setStyle(SkPaint::kStroke_Style);
        rectpaint.setColor(SK_ColorBLACK);
        rectpaint.setAntiAlias(true);
        rectpaint.setStrokeWidth(1);

        const char* text = "\n";
    size_t text_length = strlen(text);
    /* skia_render_text(resource, menlo, text, text_length, 100, 300); */
    float x,y,w,h;
    skia_text_bounds(menlo, text, text_length, &x, &y, &w, &h);
    /* fprintf(stdout, "x:%f y:%f w:%f h:%f \n", x ,y, w , h); */
    canvas->drawRect(SkRect::MakeXYWH(100, 300, w,h), rectpaint);}



    canvas->restore();

    canvas->flush();

}

/* static void run(SkCanvas* canvas) { */
/*     int display_w, display_h; */
/*     glfwGetFramebufferSize(window, &display_w, &display_h); */

/*   do { */
/*     draw(canvas); */

/*   } while (swapAndPollInput()); */
/* } */

static int swapAndPollInput() {
  glfwSwapBuffers(window);
  /* glfwPollEvents(); */
  glfwWaitEventsTimeout(0.7);
  return !glfwWindowShouldClose(window);
}

static void glfwErrorCallback(int, const char *message) {
  fprintf(stderr, "%s\n", message);
}

static void glMessageCallback(GLenum, GLenum, GLuint, GLenum, GLsizei,
                              const GLchar *message, const void *) {
    fprintf(stderr,"err: %s\n", message);
  glfwErrorCallback(0, message);
}


SkCanvas* initCanvas(int width, int height){

    glViewport(0, 0, width, height);
    glClearColor(1, 1, 1, 1);
    glClearStencil(0);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    auto interface = GrGLMakeNativeInterface();

    sk_sp<GrContext> grContext(GrContext::MakeGL(interface));
    SkASSERT(grContext);

    grContext->ref();

    GrGLint buffer = 0;

    GrGLFramebufferInfo info;
    info.fFBOID = (GrGLuint) buffer;
    SkColorType colorType;    
        info.fFormat = GL_RGBA8;
        colorType = kRGBA_8888_SkColorType;

    // If you want multisampling, uncomment the below lines and set a sample count
    static const int kStencilBits = 8;  // Skia needs 8 stencil bits
    static const int kMsaaSampleCount = 0; //4;

    int frameBufferWidth, frameBufferHeight;
    glfwGetFramebufferSize(window, &frameBufferWidth, &frameBufferHeight);

    GrBackendRenderTarget target(frameBufferWidth, frameBufferHeight, kMsaaSampleCount, kStencilBits, info);

    // setup SkSurface
    // To use distance field text, use commented out SkSurfaceProps instead
    // SkSurfaceProps props(SkSurfaceProps::kUseDeviceIndependentFonts_Flag,
    //                      SkSurfaceProps::kLegacyFontHost_InitType);
    SkSurfaceProps props(SkSurfaceProps::kLegacyFontHost_InitType);

    sk_sp<SkSurface> surface(SkSurface::MakeFromBackendRenderTarget(grContext.get(), target,
                                                                    kBottomLeft_GrSurfaceOrigin,
                                                                    colorType, nullptr, &props));
    surface->ref();

    SkCanvas* canvas = surface->getCanvas();
    canvas->scale((float)frameBufferWidth/width, (float)frameBufferHeight/height);

    /* run(canvas); */

    return canvas;

}

int main() {
  // GLFW + OpenGL init
  glfwInit();
  glfwSetErrorCallback(glfwErrorCallback);
  glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GL_TRUE);
  glfwWindowHint(GLFW_CONTEXT_ROBUSTNESS, GLFW_LOSE_CONTEXT_ON_RESET);

  int width = 1024;
  int height = 768;
  window = glfwCreateWindow(width, height, "GL test app", NULL, NULL);
  glfwMakeContextCurrent(window);

  glfwSetCursorPosCallback(window, cursor_position_callback);

  // glDebugMessageCallback(glMessageCallback, NULL);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, NULL, GL_FALSE);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_HIGH, 0, NULL, GL_TRUE);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_MEDIUM, 0, NULL, GL_TRUE);

  
    glViewport(0, 0, width, height);
    glClearColor(1, 1, 1, 1);
    glClearStencil(0);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    /* initCanvas(width, height); */

    SkiaResource* resource = skia_init();

    int frameBufferWidth, frameBufferHeight;
    glfwGetFramebufferSize(window, &frameBufferWidth, &frameBufferHeight);

    float xscale, yscale;
    glfwGetWindowContentScale(window, &xscale, &yscale);
    skia_reshape(resource, frameBufferWidth, frameBufferHeight, xscale, yscale);

    SkFont* menlo = skia_load_font("/System/Library/Fonts/Menlo.ttc", 30);

    const char s[]= "woohoo\nthere";

  do {
      skia_clear(resource);
      draw(resource);

      skia_flush(resource);
      

  } while (swapAndPollInput());    

  skia_cleanup(resource);
  // Event loop
  /* SkCanvas* canvas = initCanvas(width,height); */
  /* run(canvas); */
  // Cleanup
  glfwTerminate();
  return 0;
}
