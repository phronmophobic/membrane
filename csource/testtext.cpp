#include <stdio.h>
#include <stdlib.h>

#include <GLFW/glfw3.h>
#include <OpenGL/glext.h>

#include "text.h"

int init_resources0(){
    char *frag_source = ftgl::shader_read( "../resources/shaders/v3f-t2f-c4f.vert" );
    char *vert_source = ftgl::shader_read( "../resources/shaders/v3f-t2f-c4f.frag" );
    char *selection_frag_source = ftgl::shader_read( "../resources/shaders/selection.frag" );

    int success = init_resources(frag_source, vert_source, selection_frag_source);

    free( frag_source );
    free( vert_source );
    free( selection_frag_source );

    return success;


}

int testmain(){
    GLFWwindow* window;

    /* Initialize the library */
    if (!glfwInit())
        return -1;

    glfwWindowHint( GLFW_COCOA_RETINA_FRAMEBUFFER, 0);

    /* Create a windowed mode window and its OpenGL context */
    window = glfwCreateWindow(640, 480, "Hello World", NULL, NULL);
    if (!window)
    {
        glfwTerminate();
        return -1;
    }

    /* Make the window's context current */
    glfwMakeContextCurrent(window);

    glEnable(GL_TEXTURE_2D);
    glPixelStorei( GL_UNPACK_ALIGNMENT, 1);


    init_resources0();


    glClearColor(1,1,1,1);

    ftgl::texture_font_t* font = load_font("/System/Library/Fonts/Menlo.ttc", 22);

    double projection_matrix[16] = {
                                    0.003125,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    -0.004166666666666667,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    -1.0,
                                    0.0,
                                    -1.0,
                                    1.0,
                                    -0.0,
                                    1.0
    };

    glViewport(0, 0, 640, 480);

    /* Loop until the user closes the window */
    while (!glfwWindowShouldClose(window))
    {


        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMultMatrixd(projection_matrix);

        glMatrixMode(GL_MODELVIEW);

        /* Render here */
        glClearColor(1.0,1.0,1.0,1.0);
        glClear(GL_COLOR_BUFFER_BIT);
        glClear(GL_DEPTH_BUFFER_BIT);

        glLoadIdentity();
        glColor4d (0.0, 0.5, 0.5, 1.0);

        const char* text = "adf” abcdefjklasdf";

        glTranslatef(20, 20, 0);
        for ( int i = 0; i < 8; i ++){

            glPushAttrib(GL_CURRENT_BIT);
            glColor3d(0.6980392156862745, 0.8431372549019608, 1);
            render_selection(font, text, 0 , i);
            glPopAttrib();

            render_text(font, text , 0 , 0);


            glTranslatef(0, (int)(font->height - font->linegap), 0);

        }

        for( int i = 0; ; ++i )
            {
                const char* currentChar = text + i;
                if (*currentChar == '\0') break;

                ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
                // printf("char %c\n", *currentChar);

                // printf("  codepoint: %d\n"    , glyph->codepoint);
                // printf("  width:     %ld\n"  , glyph->width);
                // printf("  height:    %ld\n" , glyph->height);
                // printf("  offset_x:  %d\n"    , glyph->offset_x);
                // printf("  offset_y:  %d\n"    , glyph->offset_y);

                // printf("  advance_x: %f\n"    , glyph->advance_x);
                // printf("  advance_y: %f\n"    , glyph->advance_y);
                // printf("  so: %f\n"           , glyph->s0);
                // printf("  t0: %f\n"           , glyph->t0);
                // printf("  s1: %f\n"           , glyph->s1);
                // printf("  t1: %f\n"           , glyph->t1);
                // printf("  rendermode: %d\n"   , glyph->rendermode);

            }

        glFlush();

        /* Swap front and back buffers */
        glfwSwapBuffers(window);

        /* Poll for and process events */
        // glfwPollEvents();
        glfwWaitEventsTimeout(1);
    }

    glfwTerminate();

    return 0;
}

int test_create_framebuffer(){
    GLFWwindow* window;

    /* Initialize the library */
    if (!glfwInit())
        return -1;

    glfwWindowHint( GLFW_COCOA_RETINA_FRAMEBUFFER, 0);

    /* Create a windowed mode window and its OpenGL context */
    window = glfwCreateWindow(640, 480, "Hello World", NULL, NULL);
    if (!window)
    {
        glfwTerminate();
        return -1;
    }

    /* Make the window's context current */
    glfwMakeContextCurrent(window);

    glEnable(GL_TEXTURE_2D);
    glPixelStorei( GL_UNPACK_ALIGNMENT, 1);

    int success = init_resources0();

    glClearColor(1,1,1,1);

    ftgl::texture_font_t* font = load_font("/System/Library/Fonts/Menlo.ttc", 22);

    double projection_matrix[16] = {
                                    0.003125,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    -0.004166666666666667,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0,
                                    -1.0,
                                    0.0,
                                    -1.0,
                                    1.0,
                                    -0.0,
                                    1.0
    };

    const char* text = "adf” abcdefjklasdf\nasdfadsf\nasdfasdf";

    unsigned int fbo;
    unsigned int tex;


    glViewport(0, 0, 640, 480);

    int fbo_created = 0;
    int render_count = 0;

    /* Loop until the user closes the window */
    while (!glfwWindowShouldClose(window))
    {


        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMultMatrixd(projection_matrix);

        glMatrixMode(GL_MODELVIEW);

        /* Render here */
        glClearColor(1.0,1.0,1.0,1.0);
        glClear(GL_COLOR_BUFFER_BIT);
        glClear(GL_DEPTH_BUFFER_BIT);

        glLoadIdentity();
        glColor4d (0.0, 0.5, 0.5, 1.0);

        if ( render_count > 20){


            if (!fbo_created){
                printf("creating render buffer\n");

                fbo_created = 1;
                create_framebuffer(640, 480, &fbo, &tex);
                printf("tex: %ud\n", tex);

                glViewport(0, 0, 640, 480);

                glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
                // render_text(font, text , 0 , 0);
                // glMatrixMode(GL_PROJECTION);
                // glLoadIdentity();
                // glMultMatrixd(projection_matrix);

                // glMatrixMode(GL_MODELVIEW);

                /* Render here */
                glClearColor(1.0,1.0,1.0,1.0);
                glClear(GL_COLOR_BUFFER_BIT);
                glClear(GL_DEPTH_BUFFER_BIT);

                glLoadIdentity();
                glColor4d (0.0, 0.5, 0.5, 1.0);

                glTranslatef(20, 20, 0);

                render_text(font, text , 0 , 0);

                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                delete_framebuffer(fbo);
                glViewport(0, 0, 640, 480);
            }

            glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
            glBindTexture( GL_TEXTURE_2D, tex);
            
            glPushAttrib(GL_CURRENT_BIT);
            glColor3d(1,1,1);
            glBegin(GL_QUADS);

            int x = 0;
            int y = 0;
            int width = 640;
            int height = 480;

            glTexCoord2d( 0.0, 0.0); glVertex3d( x, y, 0);
            glTexCoord2d( 1.0, 0.0); glVertex3d( x + width,  y, 0);
            glTexCoord2d( 1.0, 1.0); glVertex3d( x + width,  y+ height, 0);
            glTexCoord2d( 0.0, 1.0); glVertex3d( x, y + height, 0);

            glEnd();
            glPopAttrib();

            glBindTexture(GL_TEXTURE_2D, 0);

        }

        // render_text(font, text , 0 , 0);


        glFlush();

        /* Swap front and back buffers */
        glfwSwapBuffers(window);

        /* Poll for and process events */
        // glfwPollEvents();
        glfwWaitEventsTimeout(1);
        render_count++;
    }

    glfwTerminate();

    return 0;
}



int main(void){

    return testmain();
    // return test_create_framebuffer();

}



