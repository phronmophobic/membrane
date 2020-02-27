#include <string>
#include <iostream>

#include <OpenGL/gl.h>
#include <OpenGL/glext.h>


#include "SOIL.h"


// GLuint texture[0];


// void resize(int height, int width) {
//     const float ar = (float) width / (float) height;
//     glViewport(0, 10, width, height);
//     glMatrixMode(GL_PROJECTION);
//     glLoadIdentity();

//     glFrustum(-ar, ar, -1.0, 1.0, 2.0, 90.0);
//     //gluLookAt(0, 2, 0, -1, 1, -3, 0, 1, 0);
//     glMatrixMode(GL_MODELVIEW);
//     glLoadIdentity() ;
// }
// void keyOperations (void) {
//     if (!keyStates['a']) {}
// }

extern "C" {
    GLuint loadImage(char* path){

        GLuint tex_2d = SOIL_load_OGL_texture // load an image file directly as a new OpenGL texture
            (
                path,
                SOIL_LOAD_AUTO,
                SOIL_CREATE_NEW_ID,
                /* SOIL_FLAG_MIPMAPS |  SOIL_FLAG_INVERT_Y | SOIL_FLAG_NTSC_SAFE_RGB | SOIL_FLAG_COMPRESS_TO_DXT */ SOIL_LOAD_AUTO //  | SOIL_FLAG_MULTIPLY_ALPHA
            );

        if( 0 == tex_2d )
            {
                fprintf(stdout, "SOIL loading error: '%s'\n", SOIL_last_result() );
            }

        return tex_2d;


    }
    GLuint loadImageFromMemory(const unsigned char *const buffer,int buffer_length){


        GLuint tex_2d = SOIL_load_OGL_texture_from_memory // load an image file directly as a new OpenGL texture
            (
                buffer,
                buffer_length,
                SOIL_LOAD_AUTO,
                SOIL_CREATE_NEW_ID,
                /* SOIL_FLAG_MIPMAPS |  SOIL_FLAG_INVERT_Y | SOIL_FLAG_NTSC_SAFE_RGB | SOIL_FLAG_COMPRESS_TO_DXT */ SOIL_LOAD_AUTO //  | SOIL_FLAG_MULTIPLY_ALPHA
            );

        if( 0 == tex_2d )
            {
                fprintf(stdout, "SOIL loading error: '%s'\n", SOIL_last_result() );
            }

        return tex_2d;


    }

    void drawImage(GLuint texture){
        glBindTexture(GL_TEXTURE_2D, texture);
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f); glVertex3f(-1.0f, -1.0f,  1.0f);
        glTexCoord2f(1.0f, 0.0f); glVertex3f( 1.0f, -1.0f,  1.0f);
        glTexCoord2f(1.0f, 1.0f); glVertex3f( 1.0f,  1.0f,  1.0f);
        glTexCoord2f(0.0f, 1.0f); glVertex3f(-1.0f,  1.0f,  1.0f);
        glEnd();

    }
}
// static void Draw(void)
// {
//     glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//     glLoadIdentity();
//     glTranslatef(0.0f,0.0f,-5.0f);
//     texture[0] = SOIL_load_OGL_texture // load an image file directly as a new OpenGL texture
//     (
//         "foto.png",
//         SOIL_LOAD_AUTO,
//         SOIL_CREATE_NEW_ID,
//         SOIL_FLAG_MIPMAPS | SOIL_FLAG_INVERT_Y | SOIL_FLAG_NTSC_SAFE_RGB | SOIL_FLAG_COMPRESS_TO_DXT
//     );
// // allocate a texture name

//     glBindTexture(GL_TEXTURE_2D, texture[0]);
//     glBegin(GL_QUADS);
//     glTexCoord2f(0.0f, 0.0f); glVertex3f(-1.0f, -1.0f,  1.0f);
//     glTexCoord2f(1.0f, 0.0f); glVertex3f( 1.0f, -1.0f,  1.0f);
//     glTexCoord2f(1.0f, 1.0f); glVertex3f( 1.0f,  1.0f,  1.0f);
//     glTexCoord2f(0.0f, 1.0f); glVertex3f(-1.0f,  1.0f,  1.0f);
//     glEnd();
//     glutSwapBuffers();
// }
// void keyPressed (unsigned char key, int x, int y) {
//     keyStates[key] = false;
// }
// void keyUp (unsigned char key, int x, int y) {
//     keyStates[key] = true;
// }
// int main(int argc, char **argv)
// {
//     glutInitDisplayMode(GLUT_DOUBLE | GLUT_RGB);
//     glutInit(&argc, argv);
//     glutInitWindowSize(600, 600);
//     glutCreateWindow("ugh fml");
//     glutReshapeFunc(resize);
//     glutDisplayFunc(Draw);
//     glutKeyboardFunc(keyPressed);
//     glutKeyboardUpFunc(keyUp);
//     /////////////////////////////////////
//     glEnable(GL_TEXTURE_2D);
//     glShadeModel(GL_SMOOTH);
//     glClearColor(0.0f, 0.0f, 0.0f, 0.5f);
//     glClearDepth(1.0f);
//     glEnable(GL_DEPTH_TEST);
//     glDepthFunc(GL_LEQUAL);
//     glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
//     glutMainLoop();
// }

