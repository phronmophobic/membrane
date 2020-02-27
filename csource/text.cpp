#include "text.h"

struct point {
	GLfloat x;
	GLfloat y;
	GLfloat s;
	GLfloat t;
};

typedef struct {
    float x, y, z;    // position
    float s, t;       // texture
    float r, g, b, a; // color
} vertex_t;


FT_Library ft;

ftgl::vertex_buffer_t * buffer;
GLuint shader;
GLuint selection_shader;
ftgl::mat4   model, view, projection;

extern "C" {


    int init_resources(char * vert_source, char* frag_source, char* selection_frag_source) {
    /* Initialize the FreeType2 library */
    if (FT_Init_FreeType(&ft)) {
        fprintf(stderr, "Could not init freetype library\n");
        return 0;
    }

    {
    buffer = ftgl::vertex_buffer_new( "vertex:3f,tex_coord:2f,color:4f" );

    shader = ftgl::shader_load_string(vert_source,
                                      frag_source);

    selection_shader = ftgl::shader_load_string(vert_source,
                                                selection_frag_source);


    ftgl::mat4_set_identity( &projection );
    ftgl::mat4_set_identity( &model );
    ftgl::mat4_set_identity( &view );
    }

    return 1;
}
}


/**
 * Render text using the currently loaded font and currently set font size.
 * Rendering starts at coordinates (x, y), z is always 0.
 * The pixel coordinates that the FreeType2 library uses are scaled by (sx, sy).
 */

extern "C" {

    int set_font_size(FT_Face* face, int size){
        return FT_Set_Pixel_Sizes(*face, 0, size);
    }




    void set_font_color(GLfloat color[4]){
        // glUniform4fv(uniform_color, 1, color);
        
    }


    void add_text( ftgl::vertex_buffer_t * buffer, ftgl::texture_font_t * font,
                   const char *text, ftgl::vec4 * color, ftgl::vec2 * pen ){


        float lineStartX = pen->x;
        pen->y += font->ascender;

        size_t i;
        float r = color->red, g = color->green, b = color->blue, a = color->alpha;
        // int textSize = strlen(text);
        for( i = 0; ; ++i )
            {

                const char* currentChar = text + i;
                if (*currentChar == '\0') break;

                if (*currentChar == '\n'){
                    pen->y += font->height - font->linegap;
                    pen->x = lineStartX;
                    continue;
                }

                ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
                if( glyph != NULL )
                    {
                        float kerning = 0.0f;
                        if( i > 0)
                            {
                                kerning = texture_glyph_get_kerning( glyph, text + i - 1 );
                            }
                        pen->x += kerning;

                        // new try
                        float x0  = ( pen->x + glyph->offset_x );
                        float y0  = ( pen->y - glyph->offset_y );
                        float x1  = ( x0 + glyph->width );
                        float y1  = ( y0 + glyph->height );

                        float s0 = glyph->s0;
                        float t0 = glyph->t0;
                        float s1 = glyph->s1;
                        float t1 = glyph->t1;
                        GLuint index = buffer->vertices->size;
                        GLuint indices[] = {index, index+1, index+2,
                                            index, index+2, index+3};
                        vertex_t vertices[] = { { x0,y0,0,  s0,t0,  r,g,b,a },
                                                { x0,y1,0,  s0,t1,  r,g,b,a },
                                                { x1,y1,0,  s1,t1,  r,g,b,a },
                                                { x1,y0,0,  s1,t0,  r,g,b,a } };
                        vertex_buffer_push_back_indices( buffer, indices, 6 );
                        vertex_buffer_push_back_vertices( buffer, vertices, 4 );
                        pen->x += glyph->advance_x;
                    }
            }
    }


    void render_text(ftgl::texture_font_t * font, const char* text, float x, float y){

        vertex_buffer_clear( buffer );
        glEnable( GL_TEXTURE_2D );
        // glBlendFunc( GL_ONE, GL_ONE );
        glDisable( GL_BLEND );

        size_t i;
        ftgl::vec4 color = {{1,0,0,1}};
        ftgl::vec2 pen = {{x,y}};
        add_text( buffer, font, text, &color, &pen );
        // pen.y += font->height - font->linegap;

        ftgl::texture_atlas_t * atlas = font->atlas;
        if ( atlas->id == 0 ){
            glGenTextures( 1, &atlas->id );
        }
        glBindTexture( GL_TEXTURE_2D, atlas->id );

        glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
        glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
        glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
        glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
        glTexImage2D( GL_TEXTURE_2D, 0, GL_RED, atlas->width, atlas->height,
                      0, GL_RED, GL_UNSIGNED_BYTE, atlas->data );



        glUseProgram( shader );
        {
            glUniform1i( glGetUniformLocation( shader, "texture" ),
                         0 );
            glUniformMatrix4fv( glGetUniformLocation( shader, "model" ),
                                1, 0, model.data);
            glUniformMatrix4fv( glGetUniformLocation( shader, "view" ),
                                1, 0, view.data);
            glUniformMatrix4fv( glGetUniformLocation( shader, "projection" ),
                                1, 0, projection.data);

            vertex_buffer_render( buffer, GL_TRIANGLES );
        }
        glUseProgram(0); glGetError();
        glBindTexture( GL_TEXTURE_2D, 0 );

    }

    void render_selection(ftgl::texture_font_t * font, const char* text, int selection_start, int selection_end){

        // vertex_buffer_clear( buffer );
        // glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
        // glEnable( GL_TEXTURE_2D );
        // glEnable( GL_BLEND );
        // glPushAttrib(GL_CURRENT_BIT);
        // glColor4d(0.6980392156862745,
        //           0.8431372549019608,
        //           1,
        //           1);

        // ftgl::vec4 color = {{1,0,0,1}};
        ftgl::vec2 pen = {{0,0}};

        float lineStartX = floorf(pen.x);
        pen.y += font->ascender;
        pen.y = floorf(pen.y);


        // glBegin(GL_POLYGON);
        // used because of multi-byte characters
        int offset = 0;
        size_t i;
        // float r = color.red, g = color.green, b = color.blue, a = color.alpha;
        for( i = 0; i - offset < selection_end; i++)
            {
                const char* currentChar = text + i;
                if (*currentChar == '\0') break;

                if (*currentChar == '\n'){
                    pen.y += font->height - font->linegap;
                    pen.x = lineStartX;
                    continue;
                }

                ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
                if( glyph != NULL ) {
                    float kerning = 0.0f;
                    if( i > 0)
                        {
                            kerning = texture_glyph_get_kerning( glyph, text + i - 1 );
                        }
                    pen.x += kerning;

                    if (i - offset >= selection_start){

                        int x0  = (int)( pen.x - kerning );
                        int y0  = (int)( pen.y + 1);
                        int x1  = (int)( pen.x + glyph->advance_x );
                        int y1  = (int)( y0 - font->ascender );

                        float s0 = glyph->s0;
                        float t0 = glyph->t0;
                        float s1 = glyph->s1;
                        float t1 = glyph->t1;

                        GLuint index = buffer->vertices->size;
                        // GLuint indices[] = {index, index+1, index+2,
                        //                     index, index+2, index+3};
                        // vertex_t vertices[] = { { x0,y0,0,  s0,t0,  r,g,b,a },
                        //                         { x0,y1,0,  s0,t1,  r,g,b,a },
                        //                         { x1,y1,0,  s1,t1,  r,g,b,a },
                        //                         { x1,y0,0,  s1,t0,  r,g,b,a } };
                        glRecti(x0, y0, x1, y1);
                        // vertex_buffer_push_back_indices( buffer, indices, 6 );
                        // vertex_buffer_push_back_vertices( buffer, vertices, 4 );
                    }

                    pen.x += glyph->advance_x;

                    if ( glyph->codepoint == -1 ){
                        offset += 1;
                    }
                }
            }

        // glEnd();
        // ftgl::texture_atlas_t * atlas = font->atlas;
        // glBindTexture( GL_TEXTURE_2D, atlas->id );
        // glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
        // glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
        // glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
        // glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
        // glTexImage2D( GL_TEXTURE_2D, 0, GL_RED, atlas->width, atlas->height,
        //               0, GL_RED, GL_UNSIGNED_BYTE, atlas->data );



        // glUseProgram( selection_shader );
        // {
        //     glUniformMatrix4fv( glGetUniformLocation( shader, "model" ),
        //                         1, 0, model.data);
        //     glUniformMatrix4fv( glGetUniformLocation( shader, "view" ),
        //                         1, 0, view.data);
        //     glUniformMatrix4fv( glGetUniformLocation( shader, "projection" ),
        //                         1, 0, projection.data);

        //     vertex_buffer_render( buffer, GL_TRIANGLES );
        // }
        // glUseProgram(0);
        // glBindTexture( GL_TEXTURE_2D, 0 );

        // glPopAttrib();

    }

    int index_for_position(ftgl::texture_font_t * font, const char* text, float px, float py){
        float line_height = font->height - font->linegap;
        int line = (int)(py / line_height);

        int cursor = 0;
        // to handle multi byte characters
        int offset = 0;
        for ( ;line > 0 ; ++cursor ){
            const char* currentChar = text + cursor;
            if ( *currentChar == '\0' ){
                return cursor;
            }
            if ( *currentChar == '\n' ){
                line -= 1;
            }
            ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
            if ( glyph != NULL && glyph->codepoint == -1 ){
                offset += 1;
            }

        }


        float x = 0;
        // int textSize = strlen(text);
        for( ; ; cursor ++ )
            {
                const char* currentChar = text + cursor;
                if (*currentChar == '\0') {
                    break;
                }

                if (*currentChar == '\n'){
                    break;
                }

                ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
                if( glyph != NULL ) {
                    float kerning = 0.0f;
                    if( cursor > 0) {
                        kerning = texture_glyph_get_kerning( glyph, text + cursor - 1 );
                    }
                    x += kerning;

                    if (glyph->codepoint == -1){
                        offset += 1;
                    }

                    x += glyph->advance_x;
                    if ( x > px ){
                        break;
                    }

                }
            }
        return cursor - offset;

    }

    ftgl::texture_font_t* load_font(const char* fontfilename, float fontsize){
        ftgl::texture_atlas_t * atlas = ftgl::texture_atlas_new( 512, 512, 1 );
        ftgl::texture_font_t* font = ftgl::texture_font_new_from_file( atlas, fontsize, fontfilename );

        return font;
    }

    void text_bounds(float* minx, float* miny, float* maxx, float* maxy, ftgl::texture_font_t * font, const char* text){

        *minx = std::numeric_limits<float>::max();
        *miny = std::numeric_limits<float>::max();
        *maxx = -(*minx);
        *maxy = -(*miny);

        float x = 0;
        float y = 0;
        size_t i;

        for( i = 0; ; ++i )
            {
                const char* currentChar = text + i;
                if (*currentChar == '\0') break;

                if (*currentChar == '\n'){
                    y += font->height - font->linegap;
                    x = 0;

                    *maxy = std::max(*maxy, y + font->height - font->linegap);

                    continue;
                }

                ftgl::texture_glyph_t *glyph = texture_font_get_glyph( font, currentChar );
                if( glyph != NULL )
                    {
                        float kerning = 0.0f;
                        if( i > 0)
                            {
                                kerning = texture_glyph_get_kerning( glyph, text + i - 1 );
                            }
                        x += kerning;

                        float x0  = ( x + glyph->offset_x );
                        // float y0  = ( y - glyph->offset_y );
                        float y0 = y;
                        float x1  = ( x0 + glyph->width );
                        // float y1  = ( y0 + glyph->height );
                        float y1 = y + font->height - font->linegap;
                        // fprintf(stdout, "y0 and y1: %f, %f", y0, y1);

                        x += glyph->advance_x;

                        *minx = std::min(std::min(*minx, x0), x1);
                        *miny = std::min(std::min(*miny, y0), y1);

                        *maxx = std::max(std::max(*maxx, x0), x1);
                        *maxy = std::max(std::max(*maxy, y0), y1);
                    }
            }

    }
void delete_framebuffer(GLuint frameBuffer){
    glDeleteFramebuffers(1, &frameBuffer);
}

int create_framebuffer(GLsizei width, GLsizei height, GLuint* FramebufferName, GLuint*  renderedTexture){
// int create_framebuffer(GLsizei width, GLsizei height, GLuint* _FramebufferName, GLuint*  _renderedTexture){
    // GLuint _FramebufferName;
    // GLuint _renderedTexture;
    // printf( "vals %u, %u") ;
    // GLuint __FramebufferName;
    // GLuint __renderedTexture;
    // GLuint* FramebufferName = & __FramebufferName;
    // GLuint* renderedTexture = & __renderedTexture;

    glGenFramebuffers(1, FramebufferName);
    glBindFramebuffer(GL_FRAMEBUFFER, *FramebufferName);

    glGenTextures(1, renderedTexture);

// "Bind" the newly created texture : all future texture functions will modify this texture
    glBindTexture(GL_TEXTURE_2D, *renderedTexture);

// Give an empty image to OpenGL ( the last "0" )
    glTexImage2D(GL_TEXTURE_2D, 0,GL_RGBA, width, height, 0,GL_RGBA, GL_UNSIGNED_BYTE, 0);

// Poor filtering. Needed !
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

    GLuint depthrenderbuffer;
    glGenRenderbuffers(1, &depthrenderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, depthrenderbuffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, width, height);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthrenderbuffer);


// Set "renderedTexture" as our colour attachement #0
    glFramebufferTextureEXT(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, *renderedTexture, 0);

// Set the list of draw buffers.
    GLenum DrawBuffers[1] = {GL_COLOR_ATTACHMENT0};
    glDrawBuffers(1, DrawBuffers); // "1" is the size of DrawBuffers


    if(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        return 0;

    return 1;

};

}



