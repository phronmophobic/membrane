#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <limits>
#include <algorithm>
// #include <GLFW/glfw3.h>
#include <OpenGL/glext.h>
// #include <fstream>

#define GLM_FORCE_RADIANS

#include <ft2build.h>
#include FT_FREETYPE_H

#include "shader_utils.h"
#include "vertex-buffer.h"
#include "texture-font.h"
#include "mat4.h"
#include "shader.h"

extern "C" {
    int init_resources(char * vert_source, char* frag_source, char* selection_frag_source);
    void render_text(ftgl::texture_font_t * font, const char* text, float x, float y);
    int create_framebuffer(GLsizei width, GLsizei height, GLuint* FramebufferName, GLuint*  renderedTexture);
    ftgl::texture_font_t* load_font(const char* fontfilename, float fontsize);
    void render_selection(ftgl::texture_font_t * font, const char* text, int selection_start, int selection_end);
    void delete_framebuffer(GLuint frameBuffer);
}
