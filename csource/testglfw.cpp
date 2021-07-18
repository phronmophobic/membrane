#define GLFW_INCLUDE_GLCOREARB
#define GL_GLEXT_PROTOTYPES

#include <GLFW/glfw3.h>
#include <stdio.h>

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

// term stuff
#include <errno.h>
#include <fcntl.h>

extern "C" {
#include <tmt.h>
}

#define LOG(fmt, ...) \
            do {FILE *fp; fp = fopen("/var/tmp/cef.log", "a");fprintf(fp, fmt, __VA_ARGS__); fclose(fp);} while (0)


/* Includes needed to make forkpty(3) work. */
#ifndef FORKPTY_INCLUDE_H
    #if defined(__APPLE__)
        #define FORKPTY_INCLUDE_H <util.h>
    #elif defined(__FreeBSD__)
        #define FORKPTY_INCLUDE_H <libutil.h>
    #else
        #define FORKPTY_INCLUDE_H <pty.h>
    #endif
#endif
#include FORKPTY_INCLUDE_H

static GLFWwindow *window = NULL;
static int swapAndPollInput();

double xpos = 0 ;
double ypos = 0;
void cursor_position_callback(GLFWwindow* window, double _xpos, double _ypos)
{
    xpos = _xpos;
    ypos = _ypos;
}



static char iobuf[BUFSIZ];
#define DEFAULT_TERMINAL "screen-bce"
#define DEFAULT_256_COLOR_TERMINAL "screen-256color-bce"

#define SENDN(n, s, c) safewrite(n->pt, s, c)
#define SEND(n, s) SENDN(n, s, strlen(s))

static void
safewrite(int fd, const char *b, size_t n) /* Write, checking for errors. */
{
    size_t w = 0;
    while (w < n){
        ssize_t s = write(fd, b + w, n - w);
        if (s < 0 && errno != EINTR)
            return;
        else if (s < 0)
            s = 0;
        w += (size_t)s;
    }
}

int pt = 0;

SkiaResource* termresource = NULL;
unsigned short termw = 80;
unsigned short termh = 40;
int cursorw = 10;
int cursorh = 13;
SkFont* menlo = NULL;
TMT *vt;
int wants_redraw = 0;

void key_callback(GLFWwindow* window, int key, int scancode, int action, int mods){

    if (!pt) return;

    if ( key == GLFW_KEY_R ){
        fprintf(stdout, "requesting redraw\n");
        wants_redraw = 1;
    }


    // char c = (char)key;
    if ( action == 1 || action == 2){

        switch(key){


        case GLFW_KEY_UP        : write(pt, TMT_KEY_UP, strlen(TMT_KEY_UP))               ; break ;
        case GLFW_KEY_DOWN      : write(pt, TMT_KEY_DOWN, strlen(TMT_KEY_DOWN))           ; break ;
        case GLFW_KEY_RIGHT     : write(pt, TMT_KEY_RIGHT, strlen(TMT_KEY_RIGHT))         ; break ;
        case GLFW_KEY_LEFT      : write(pt, TMT_KEY_LEFT, strlen(TMT_KEY_LEFT))           ; break ;
        case GLFW_KEY_HOME      : write(pt, TMT_KEY_HOME, strlen(TMT_KEY_HOME))           ; break ;
        case GLFW_KEY_END       : write(pt, TMT_KEY_END, strlen(TMT_KEY_END))             ; break ;
        case GLFW_KEY_INSERT    : write(pt, TMT_KEY_INSERT, strlen(TMT_KEY_INSERT))       ; break ;
        case GLFW_KEY_BACKSPACE : write(pt, "\x7f", 1) ; break ;
        case GLFW_KEY_ESCAPE    : write(pt, TMT_KEY_ESCAPE, strlen(TMT_KEY_ESCAPE))       ; break ;
        case GLFW_KEY_TAB       : write(pt, "\t", 1)   ; break ;
        case GLFW_KEY_PAGE_UP   : write(pt, TMT_KEY_PAGE_UP, strlen(TMT_KEY_PAGE_UP))     ; break ;
        case GLFW_KEY_PAGE_DOWN : write(pt, TMT_KEY_PAGE_DOWN, strlen(TMT_KEY_PAGE_DOWN)) ; break ;
        case GLFW_KEY_F1        : write(pt, TMT_KEY_F1,strlen(TMT_KEY_F1))                ; break ;
        case GLFW_KEY_F2        : write(pt, TMT_KEY_F2,strlen(TMT_KEY_F2))                ; break ;
        case GLFW_KEY_F3        : write(pt, TMT_KEY_F3,strlen(TMT_KEY_F3))                ; break ;
        case GLFW_KEY_F4        : write(pt, TMT_KEY_F4,strlen(TMT_KEY_F4))                ; break ;
        case GLFW_KEY_F5        : write(pt, TMT_KEY_F5,strlen(TMT_KEY_F5))                ; break ;
        case GLFW_KEY_F6        : write(pt, TMT_KEY_F6,strlen(TMT_KEY_F6))                ; break ;
        case GLFW_KEY_F7        : write(pt, TMT_KEY_F7,strlen(TMT_KEY_F7))                ; break ;
        case GLFW_KEY_F8        : write(pt, TMT_KEY_F8,strlen(TMT_KEY_F8))                ; break ;
        case GLFW_KEY_F9        : write(pt, TMT_KEY_F9,strlen(TMT_KEY_F9))                ; break ;
        case GLFW_KEY_F10       : write(pt, TMT_KEY_F10,strlen(TMT_KEY_F10))              ; break ;
        case GLFW_KEY_ENTER     : write(pt, "\r", 1)                                      ; break ;
        case GLFW_KEY_SPACE     : write(pt, " ", 1)                                       ; break ;
        case GLFW_KEY_LEFT_SHIFT     :                                                         ; break ;
        case GLFW_KEY_RIGHT_SHIFT     :                                                         ; break ;

        default:


            if (mods & GLFW_MOD_ALT  ){
                if ( key < 128 ){

                    char message[2];
                    message[0] = 0x1b;
                    // message[1] = key - 'A' + 1;

                    if ( mods & GLFW_MOD_SHIFT ){
                        message[1] = key;
                    }else{
                        message[1] = key - ('A' - 'a');
                    }
                    fprintf( stdout, "M-%c\n", message[1]);
                    fprintf(stdout , "sending : %x, %c, %x , \n", key, message[1], message[0]);
                    write(pt, message, sizeof(message));
                }                
            } else if ( mods & GLFW_MOD_CONTROL ){
                if ( key < 128 ){
                    char c = key - 'A' + 1;
                    write(pt, &c, 1);
                }
                fprintf(stdout,"control %c, %d, %d\n", (char)key, key, scancode);
            }else{

                // char c;
                // if ( mods & GLFW_MOD_SHIFT ){
                //     c = key;
                // }else{
                //     c = key - ('A' - 'a');
                // }

                // fprintf(stdout , "sending :  %c \n", key);
                // write(pt, &c, 1);                
            }

        }

    }
}

void char_callback(GLFWwindow* window, unsigned int codepoint){
    // if (!pt) return;

    if ( codepoint < 128 ){
        char c = (char)codepoint;
        fprintf(stdout, "sending char %c\n", c);
        write(pt,&c, 1);
    }

}

int screenshot = 0;
void
callback(tmt_msg_t m, TMT *vt, const void *a, void *p)
{
    if ( ! termresource){
        fprintf(stderr, "trying to call back without resourcs \n");
    }
    /* grab a pointer to the virtual screen */
    const TMTSCREEN *s = tmt_screen(vt);
    const TMTPOINT *c = tmt_cursor(vt);
    SkCanvas* canvas = termresource->surface->getCanvas();

    if ( wants_redraw){
        canvas->clear(SK_ColorWHITE);
        wants_redraw = false;
    }

    int x,y;
    SkRect rect;

    canvas->save();
    switch (m){
        case TMT_MSG_BELL:
            /* the terminal is requesting that we ring the bell/flash the
             * screen/do whatever ^G is supposed to do; a is NULL
             */
            printf("bing!\n");
            break;

        case TMT_MSG_UPDATE:


            skia_push_paint(termresource);            
            skia_set_color(termresource, 1,1,1, 1);

            rect = SkRect::MakeXYWH(0,0, 75, cursorh*termh);
            termresource->surface->getCanvas()->drawRect(rect, termresource->getPaint());
            skia_pop_paint(termresource);


            /* the screen image changed; a is a pointer to the TMTSCREEN */
            for (size_t r = 0; r < s->nline; r++){
                bool is_bold = false;;
                bool is_dirty = false;

                canvas->save();
                canvas->translate(75,0);
                if (s->lines[r]->dirty){
                    for (size_t c = 0; c < s->ncol; c++){
                        // printf("contents of %zd,%zd: %lc (%s bold)\n", r, c,
                        //        s->lines[r]->chars[c].c,
                        //        s->lines[r]->chars[c].a.bold? "is" : "is not");
                        x = c*cursorw;
                        y = (r+1)*cursorh ;
                        is_bold = is_bold || s->lines[r]->chars[c].a.bold;
                        is_dirty = is_dirty || s->lines[r]->dirty;

                        skia_push_paint(termresource);

                        skia_set_color(termresource, 1,1,1, 1);
                        rect = SkRect::MakeXYWH(x, y-cursorh, cursorw, cursorh);
                        termresource->surface->getCanvas()->drawRect(rect, termresource->getPaint());
                        
                        skia_set_color(termresource, 0,0,0, 1);

                        switch (s->lines[r]->chars[c].a.fg){

                        case TMT_COLOR_DEFAULT: skia_set_color(termresource, 0,0,0,0.8); break;
                        case TMT_COLOR_BLACK: skia_set_color(termresource, 0,0,0,0.8); break;
                        case TMT_COLOR_RED: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_GREEN: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_YELLOW: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_BLUE: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_MAGENTA: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_CYAN: skia_set_color(termresource, 1,0,0,1); break;
                        case TMT_COLOR_WHITE: skia_set_color(termresource, 1,0,0,1); break;
                        default: break;
                        }
                        
                        if ( s->lines[r]->chars[c].a.invisible ){
                            skia_set_color(termresource, 0,1,0,1); 
                        }
                        


                        
                        char letter = s->lines[r]->chars[c].c;

                        canvas->drawSimpleText(&letter, 1 , SkTextEncoding::kUTF8, x, y ,*menlo, termresource->getPaint());
                        
                        skia_pop_paint(termresource);
                    }
                }
                canvas->restore();

                char msg[10];
                snprintf(msg, 10, "%d,%d,%lu", is_dirty, is_bold, r);
                canvas->drawSimpleText(msg, strlen(msg) , SkTextEncoding::kUTF8, 0, (r+1)*cursorh ,*menlo, termresource->getPaint());
            }



            /* let tmt know we've redrawn the screen */
            tmt_clean(vt);
            break;

        case TMT_MSG_ANSWER:
            /* the terminal has a response to give to the program; a is a
             * pointer to a string */
            printf("terminal answered %s\n", (const char *)a);
            break;

        case TMT_MSG_MOVED:
            /* the cursor moved; a is a pointer to the cursor's TMTPOINT */

            skia_push_paint(termresource);            
            skia_set_color(termresource, 0.57, 0.57,0.57, 0.4f);

            x = (c->c)*cursorw;
            y = (c->r)*cursorh ;
            rect = SkRect::MakeXYWH(x+75, y, cursorw, cursorh);
            termresource->surface->getCanvas()->drawRect(rect, termresource->getPaint());
            skia_pop_paint(termresource);
            // tmt_clean(vt);
            
            break;

    case TMT_MSG_CURSOR:
            // printf("cursor is now at %zd,%zd\n", c->r, c->c);

            skia_push_paint(termresource);            
            skia_set_color(termresource, 0.57, 0.57,0.57, 0.4f);

            x = (c->c)*cursorw;
            y = (c->r)*cursorh ;
            rect = SkRect::MakeXYWH(x+75, y, cursorw, cursorh);
            termresource->surface->getCanvas()->drawRect(rect, termresource->getPaint());
            skia_pop_paint(termresource);
        
            // tmt_clean(vt);
            break;        
    }
    canvas->restore();
    termresource->surface->flush();

    sk_sp<SkImage> img(termresource->surface->makeImageSnapshot());
    SkEncodedImageFormat fmt = SkEncodedImageFormat::kPNG;
    sk_sp<SkData> img_data(img->encodeToData(fmt, 100));
    char path[256]; 
    snprintf(path, 256, "screenshots/ss-%d.png", screenshot++);
    SkFILEWStream out(path);
    out.write(img_data->data(), img_data->size());

}


void newterm(SkiaResource* resource){

    struct winsize ws = {.ws_row = termh, .ws_col = termw};
    pid_t pid = forkpty(&pt, NULL, NULL, &ws);

    // int* pt = NULL;

    if (pid < 0){
        fprintf(stderr, "forking error! \n");
        exit(1);
    } else if (pid == 0){


        setsid();
        // setenv("MTM", buf, 1);
        setenv("TERM", DEFAULT_TERMINAL, 1);
        signal(SIGCHLD, SIG_DFL);
        execl("/bin/bash", "/bin/bash", NULL);
        return;
    }

    fcntl(pt, F_SETFL, O_NONBLOCK);

    fprintf(stdout, "making size : %d, %d\n", (termw*cursorw+ 20)* 2, (termh*cursorh+20)* 2);
    termresource = skia_offscreen_buffer(resource, (termw*cursorw+ 200)* 2, (termh*cursorh+20)* 2);
    {
        SkCanvas* termCanvas = termresource->surface->getCanvas();
        termCanvas->scale(2,2);
        termCanvas->clear(SK_ColorWHITE);

    }

    vt = tmt_open(termh, termw, callback, NULL, NULL);

}

void runterm(){

    ssize_t r;
    do{
        r = read(pt, iobuf, sizeof(iobuf));
        if (r > 0){
            tmt_write(vt, iobuf, r);
        } else if (r <= 0 && errno != EINTR && errno != EWOULDBLOCK){
            fprintf(stderr, "error reading!\n");
            exit(1);
        }
    } while( r > 0);
        

}


void draw(SkiaResource* resource){

    SkCanvas* canvas = resource->surface->getCanvas();

    // canvas->save();

    // canvas->clipRect(SkRect::MakeWH(100, 100));
    // canvas->clear(SK_ColorRED);
    // canvas->scale(.5, .5);
    // canvas->clipRect(SkRect::MakeWH(100, 100));
    // canvas->clear(SK_ColorBLUE);


    // canvas->restore();



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

    // if ( !img ){

    //     SkiaResource* gpuResource = skia_offscreen_buffer(resource, 800*2, 800*2);

    //     {
    //         SkCanvas* gpuCanvas = gpuResource->surface->getCanvas();
    //         gpuCanvas->scale(2,2);
    //         // gpuCanvas->clear(SK_ColorWHITE);
            
    //         char stext [] = "The quick brown fox jumped over the lazy dog";

    //         skia_render_line(gpuResource, menlo,  stext, strlen(stext), 0, 0);
            

    //         SkPaint rectpaint;
    //         rectpaint.setStyle(SkPaint::kFill_Style);
    //         rectpaint.setColor(SK_ColorBLACK);
    //         rectpaint.setAntiAlias(true);
    //         rectpaint.setStrokeWidth(1);
    //         rectpaint.setAlpha(128);

    //         gpuCanvas->drawRect(SkRect::MakeXYWH(0, 0, 50,50), rectpaint);}
    //     img = skia_offscreen_image(gpuResource);
    // }    


    canvas->save();
    canvas->translate(5,5);
    canvas->scale(0.5,0.5);
    termresource->surface->draw(canvas, 0, 0, nullptr);
    canvas->restore();



    // SkPaint paint;
    // paint.setAntiAlias(true);
    
    // canvas->save();
    // canvas->translate(100,80);
    // canvas->drawImageRect(img, SkRect::MakeXYWH(0, 0, 800, 800), &paint);
    
    // canvas->restore();



    // paint.setColor(SK_ColorBLACK);


    // {
    //     const char* text[] = {"The quick brown",
    //                          "fox jumped over",
    //                          "the lzy dog",
    //                          "",
    //                          "dood."};

    //     canvas->save();

    //     canvas->translate(100,100);

    //     int index = skia_index_for_position(menlo, text[0], strlen(text[0]), xpos - 100);


    //     if ( index > 1 ){

    //         // skia_render_selection(resource, menlo, text[0], strlen(text[0]), 1, index);
    //         skia_push_paint(resource);

    //         skia_set_color(resource, 1, 0,0, 0.5f);
    //         skia_render_cursor(resource, menlo, text[0], strlen(text[0]), index);

    //         skia_pop_paint(resource);

    //     }

    //     canvas->save();
    //     for ( int i = 0; i < 5; i++){
    //         skia_render_line(resource, menlo, text[i], strlen(text[i]), 0, 0);
    //         skia_next_line(resource,menlo);
    //     }
    //     canvas->restore();

        
    //     canvas->translate(10,300);
    //     skia_render_cursor(resource, menlo, "", 0, 0);




    //     canvas->restore();

    //     /* auto textblob = SkTextBlob::MakeFromString(text, *menlo); */
    //     /* canvas->drawTextBlob(textblob.get(), 50, 25, paint); */
    //     /* canvas->drawString(text, 100, 100, *menlo, paint); */

        
    // }

    // canvas->save();
    // {
    //     SkPaint rectpaint;
    //     rectpaint.setStyle(SkPaint::kStroke_Style);
    //     rectpaint.setColor(SK_ColorBLACK);
    //     rectpaint.setAntiAlias(true);
    //     rectpaint.setStrokeWidth(1);

    //     const char* text = "\n";
    // size_t text_length = strlen(text);
    // /* skia_render_text(resource, menlo, text, text_length, 100, 300); */
    // float x,y,w,h;
    // skia_text_bounds(menlo, text, text_length, &x, &y, &w, &h);
    // /* fprintf(stdout, "x:%f y:%f w:%f h:%f \n", x ,y, w , h); */
    // canvas->drawRect(SkRect::MakeXYWH(100, 300, w,h), rectpaint);}



    // canvas->restore();

    // canvas->flush();

}

/* static void run(SkCanvas* canvas) { */
/*     int display_w, display_h; */
/*     glfwGetFramebufferSize(window, &display_w, &display_h); */

/*   do { */
/*     draw(canvas); */

/*   } while (swapAndPollInput()); */
/* } */

static int swapAndPollInput() {
  glfwPollEvents();
  // glfwWaitEventsTimeout(0.2);
  glfwSwapBuffers(window);
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

    // auto interface = GrGLMakeNativeInterface();

    sk_sp<GrDirectContext> grContext(GrDirectContext::MakeGL());
    SkASSERT(grContext);

    grContext->ref();

    GrGLint buffer = 0;

    GrGLFramebufferInfo info;
    info.fFBOID = (GrGLuint) buffer;
    SkColorType colorType;    
    info.fFormat = GL_RGBA8;
    colorType = kRGBA_8888_SkColorType;

    // If you want multisampling, uncomment the below lines and set a sample count
    int kStencilBits = 8;  // Skia needs 8 stencil bits
    int kMsaaSampleCount = 0; //4;

    int frameBufferWidth, frameBufferHeight;
    glfwGetFramebufferSize(window, &frameBufferWidth, &frameBufferHeight);

    GrBackendRenderTarget* target = new GrBackendRenderTarget(frameBufferWidth, frameBufferHeight, kMsaaSampleCount, kStencilBits, info);

    
    // setup SkSurface
    // To use distance field text, use commented out SkSurfaceProps instead
    // SkSurfaceProps props(SkSurfaceProps::kUseDeviceIndependentFonts_Flag,
    //                      SkSurfaceProps::kLegacyFontHost_InitType);

    // sk_sp<SkColorSpace> colorSpace(SkColorSpace::MakeSRGB());
    SkSurfaceProps surfaceProps(0, kUnknown_SkPixelGeometry);

    sk_sp<SkSurface> surface(SkSurface::MakeFromBackendRenderTarget(grContext.get(), *target,
                                                                    kBottomLeft_GrSurfaceOrigin,
                                                                    colorType, nullptr, &surfaceProps));
    surface->ref();
    delete target;

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
  // glfwSetKeyCallback(window, key_callback);
  // glfwSetCharCallback(window, char_callback);

  

  // glDebugMessageCallback(glMessageCallback, NULL);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, NULL, GL_FALSE);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_HIGH, 0, NULL, GL_TRUE);
  // glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_MEDIUM, 0, NULL, GL_TRUE);
  // glfwSwapInterval(1);
  
    glViewport(0, 0, width, height);
    glClearColor(1, 1, 1, 1);
    glClearStencil(0);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    /* initCanvas(width, height); */

    SkCanvas* canvas = initCanvas(width, height);

    LOG("CANVAS: %p\n", canvas);

      do {

          LOG("CANVAS: %p\n", canvas);

          canvas->clear(SK_ColorGREEN);

          SkPaint paint;
          paint.setColor(SK_ColorBLACK);
          paint.setAntiAlias(true);
    
          canvas->save();
          canvas->translate(100,80);
          canvas->drawRect(SkRect::MakeXYWH(0, 0, 800,800), paint);
          canvas->restore();

          canvas->flush();
	LOG("flush: %p\n", canvas);
      

      } while (swapAndPollInput());    

      return 1;


    SkiaResource* resource = NULL; //skia_init();




    int frameBufferWidth, frameBufferHeight;
    glfwGetFramebufferSize(window, &frameBufferWidth, &frameBufferHeight);

    float xscale, yscale;
    glfwGetWindowContentScale(window, &xscale, &yscale);


    skia_reshape(resource, frameBufferWidth, frameBufferHeight, xscale, yscale);

    menlo = skia_load_font("/System/Library/Fonts/Menlo.ttc", cursorh);
    // menlo_bold = new SkFont(SkTypeface::MakeFromName("Menlo", SkFontStyle::Bold()), fontSize);

    const char s[]= "woohoo\nthere";

    newterm(resource);


    write(pt, "emacs", strlen("emacs"));
    char enter = 13;
    write(pt, &enter, 1);

    write(pt, "\x18", 1);
    write(pt, "2", 1);
    
    

  do {
      skia_clear(resource);
      draw(resource);
      runterm();

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
