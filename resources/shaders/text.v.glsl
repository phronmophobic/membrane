/*

attribute vec4 coord;
varying vec2 texpos;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main(void) {
   gl_Position = gl_ModelViewProjectionMatrix*vec4(coord.xy,0,1);
  texpos = coord.zw;
  gl_FrontColor = gl_Color;
}
*/

void main()
{
      gl_FragColor = gl_Color;
}
