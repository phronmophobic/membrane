''
''
''	Jonathan Dummer
''	Simple OpenGL Image Library
''	MIT License
''
''	I'm using Sean's Tool Box image loader as a base:
''	http://www.nothings.org/
''
''	Note: header translated with help of SWIG FB wrapper
''	Check the SOIL.h file for more information
''
#ifndef __SOIL_bi__
#define __SOIL_bi__

#inclib "SOIL"

enum 
	SOIL_LOAD_AUTO = 0
	SOIL_LOAD_L = 1
	SOIL_LOAD_LA = 2
	SOIL_LOAD_RGB = 3
	SOIL_LOAD_RGBA = 4
end enum

enum 
	SOIL_CREATE_NEW_ID = 0
end enum

enum 
	SOIL_FLAG_POWER_OF_TWO = 1
	SOIL_FLAG_MIPMAPS = 2
	SOIL_FLAG_TEXTURE_REPEATS = 4
	SOIL_FLAG_MULTIPLY_ALPHA = 8
	SOIL_FLAG_INVERT_Y = 16
	SOIL_FLAG_COMPRESS_TO_DXT = 32
	SOIL_FLAG_DDS_LOAD_DIRECT = 64
	SOIL_FLAG_NTSC_SAFE_RGB = 128
	SOIL_FLAG_CoCg_Y = 256
	SOIL_FLAG_TEXTURE_RECTANGLE = 512
end enum

enum 
	SOIL_SAVE_TYPE_TGA = 0
	SOIL_SAVE_TYPE_BMP = 1
	SOIL_SAVE_TYPE_DDS = 2
end enum

const SOIL_DDS_CUBEMAP_FACE_ORDER = "EWUDNS"

declare function SOIL_load_OGL_texture cdecl alias "SOIL_load_OGL_texture" (byval filename as zstring ptr, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_load_OGL_cubemap cdecl alias "SOIL_load_OGL_cubemap" (byval x_pos_file as zstring ptr, byval x_neg_file as zstring ptr, byval y_pos_file as zstring ptr, byval y_neg_file as zstring ptr, byval z_pos_file as zstring ptr, byval z_neg_file as zstring ptr, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_load_OGL_single_cubemap cdecl alias "SOIL_load_OGL_single_cubemap" (byval filename as zstring ptr, byval face_order as zstring ptr, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_load_OGL_texture_from_memory cdecl alias "SOIL_load_OGL_texture_from_memory" (byval buffer as ubyte ptr, byval buffer_length as integer, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_load_OGL_cubemap_from_memory cdecl alias "SOIL_load_OGL_cubemap_from_memory" (byval x_pos_buffer as ubyte ptr, byval x_pos_buffer_length as integer, byval x_neg_buffer as ubyte ptr, byval x_neg_buffer_length as integer, byval y_pos_buffer as ubyte ptr, byval y_pos_buffer_length as integer, byval y_neg_buffer as ubyte ptr, byval y_neg_buffer_length as integer, byval z_pos_buffer as ubyte ptr, byval z_pos_buffer_length as integer, byval z_neg_buffer as ubyte ptr, byval z_neg_buffer_length as integer, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_load_OGL_single_cubemap_from_memory cdecl alias "SOIL_load_OGL_single_cubemap_from_memory" (byval buffer as ubyte ptr, byval buffer_length as integer, byval face_order as zstring ptr, byval force_channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_create_OGL_texture cdecl alias "SOIL_create_OGL_texture" (byval data as ubyte ptr, byval width as integer, byval height as integer, byval channels as integer, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_create_OGL_single_cubemap cdecl alias "SOIL_create_OGL_single_cubemap" (byval data as ubyte ptr, byval width as integer, byval height as integer, byval channels as integer, byval face_order as zstring ptr, byval reuse_texture_ID as uinteger, byval flags as uinteger) as uinteger
declare function SOIL_save_screenshot cdecl alias "SOIL_save_screenshot" (byval filename as zstring ptr, byval image_type as integer, byval x as integer, byval y as integer, byval width as integer, byval height as integer) as integer
declare function SOIL_load_image cdecl alias "SOIL_load_image" (byval filename as zstring ptr, byval width as integer ptr, byval height as integer ptr, byval channels as integer ptr, byval force_channels as integer) as ubyte ptr
declare function SOIL_load_image_from_memory cdecl alias "SOIL_load_image_from_memory" (byval buffer as ubyte ptr, byval buffer_length as integer, byval width as integer ptr, byval height as integer ptr, byval channels as integer ptr, byval force_channels as integer) as ubyte ptr
declare function SOIL_save_image cdecl alias "SOIL_save_image" (byval filename as zstring ptr, byval image_type as integer, byval width as integer, byval height as integer, byval channels as integer, byval data as ubyte ptr) as integer
declare sub SOIL_free_image_data cdecl alias "SOIL_free_image_data" (byval img_data as ubyte ptr)
declare function SOIL_last_result cdecl alias "SOIL_last_result" () as zstring ptr

#endif
