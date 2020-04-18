# setuptools: language = c++
import multiprocessing
import numpy as np

from libcpp.vector cimport vector
from libcpp cimport bool
cimport numpy as np

__all__ = ['PARAMS', 'bm3d', 'bm3d_raw', 'simple_bm3d']

cdef extern from "../bm3d/bm3d.h":
    cdef int YUV      # 0
    cdef int YCBCR    # 1
    cdef int OPP      # 2
    cdef int RGB      # 3
    cdef int DCT      # 4
    cdef int BIOR     # 5
    cdef int HADAMARD # 6

CONSTS = {'YUV': YUV,
          'YCBCR': YCBCR,
          'OPP': OPP,
          'RGB': RGB,
          'DCT': DCT,
          'BIOR': BIOR,
          'HADAMARD': HADAMARD
          }
PARAMS = {'color_space': {'YUV': 0, 'YCBCR': 1, 'OPP': 2, 'RGB': 3},
          'tau_2D_hard': {'DCT': 4, 'BIOR': 5,},
          'tau_2D_wien': {'DCT': 4, 'BIOR': 5,}}


cdef extern from "../bm3d/bm3d.h":
    int run_bm3d(
        const float sigma,
        vector[float] &img_noisy,
        vector[float] &img_basic,
        vector[float] &img_denoised,
        const unsigned width,
        const unsigned height,
        const unsigned chnls,
        const bool useSD_h,
        const bool useSD_w,
        const unsigned tau_2D_hard,
        const unsigned tau_2D_wien,
        const unsigned color_space,
        const unsigned patch_size,
        const unsigned num_threads,
        const bool verbose)

    cdef bint _NO_OPENMP


cpdef float[:,:,:] bm3d_raw(
    float[:,:,:] input_array,
    float sigma,
    bool useSD_h=False,
    bool useSD_w=False,
    str tau_2D_hard="BIOR",
    str tau_2D_wien="DCT",
    str color_space="OPP",
    int patch_size=0,
    int num_threads=0,
    bool verbose=False):
    """
    sigma: value of assumed noise of the noisy image
    patch_size: overrides the default patch size selection.
    patch_size=0: use default behavior
    patch_size>0: size to be used

    input_array : input image, H x W x channum

    useSD_h (resp. useSD_w): if true, use weight based
    on the standard variation of the 3D group for the
    first (resp. second) step, otherwise use the number
    of non-zero coefficients after Hard Thresholding
    (resp. the norm of Wiener coefficients);

    tau_2D_hard (resp. tau_2D_wien): 2D transform to apply
    on every 3D group for the first (resp. second) part.
    Allowed values are 'DCT' and 'BIOR';
    """
    print("BM3D RAW locally compiled launching with fixed Color channels")
    if num_threads < 0:
        raise ValueError("Parameter num_threads must be 0 (default behavior) "
                         "or larger than 0.")

    if _NO_OPENMP and num_threads > 1:
        raise ValueError("Parameter num_threads={} must not exceed 1 if "
                         "OpenMP multithreading is not available. Please "
                         "reinstall PyBM3D with OpenMP compatible "
                         "compiler.".format(num_threads))

    num_cpus = multiprocessing.cpu_count()
    if num_threads > multiprocessing.cpu_count():
        raise ValueError("Parameter num_threads={} must not exceed the number "
                         "of real cores {}.".format(num_threads, num_cpus))

    tau_2D_hard_i = PARAMS['tau_2D_hard'].get(tau_2D_hard)
    if tau_2D_hard_i is None:
        raise ValueError("Parameter value tau_2D_hard={} is unknown. Please "
                         "select {}.".format(tau_2D_hard,
                                             list(PARAMS['tau_2D_hard'].keys())))

    tau_2D_wien_i = PARAMS['tau_2D_wien'].get(tau_2D_wien)
    if tau_2D_wien_i is None:
        raise ValueError("Parameter value tau_2D_wien={} is unknown. Please "
                         "select {}.".format(tau_2D_wien,
                                             list(PARAMS['tau_2D_wien'].keys())))

    color_space_i = PARAMS['color_space'].get(color_space)
    if color_space_i is None:
        raise ValueError("Parameter value color_space={} is unknown. Please "
                         "select {}.".format(color_space,
                                             list(PARAMS['color_space'].keys())))

    if patch_size < 0:
        raise ValueError("Parameter patch_size must be 0 (default behavior) "
                         "or larger than 0.")

    cdef vector[float] input_image, basic_image, output_image

    height = input_array.shape[0]
    width = input_array.shape[1]
    chnls = input_array.shape[2]

    # convert the input image
    input_image.resize(input_array.size)
    pos = 0

    for c in range(input_array.shape[2]):
        for y in range(input_array.shape[0]):
            for x in range(input_array.shape[1]):
                input_image[pos] = input_array[y, x, c]
                pos +=1

    ret = run_bm3d(sigma, input_image, basic_image, output_image,
                   width, height, chnls,
                   useSD_h, useSD_w,
                   tau_2D_hard_i, tau_2D_wien_i,
                   color_space_i,
                   patch_size,
                   num_threads,
                   verbose)
    if ret != 0:
        raise Exception("Executing the C function `run_bmd3d` returned "
                        "with an error: {%d}".format(ret))

    cdef np.ndarray output_array = np.zeros([height, width, chnls],
                                                dtype=np.float32)

    pos = 0
    for c in range(input_array.shape[2]):
        for y in range(input_array.shape[0]):
            for x in range(input_array.shape[1]):
                output_array[y, x, c] = output_image[pos]
                pos +=1

    return output_array


def bm3d(input_array, *args, clip=True, **kwargs):
    """Applies BM3D to the given input_array.

    This function calls the Cython wrapped run_bm3d C function. Before inputs
    are preprocessed:
        1. Convert to type Float
        2. If necessary, add third channel dimension
        """
    input_array = np.array(input_array)
    initial_shape, initial_dtype = input_array.shape, input_array.dtype

    if not np.issubdtype(initial_dtype, np.integer):
        raise TypeError("The given data type {} is not supported. Please "
                        "provide input of type integer.".format(initial_dtype))

    input_array = np.atleast_3d(input_array).astype(np.float32)

    if input_array.shape[2] not in [1, 3]:
        raise IndexError("The given shape {} is not supported. Please provide "
                         "input with 1 or 3 channels.".format(initial_shape))

    out = bm3d_raw(input_array, *args, **kwargs)

    if clip:
        dtype_info = np.iinfo(initial_dtype)

    out = np.clip(out, dtype_info.min, dtype_info.max)
    out = np.array(out, dtype=initial_dtype).reshape(initial_shape)

    return out


def simple_bm3d(img : np.ndarray):
    cdef bool useSD_1 = False
    cdef bool useSD_2 = True
    cdef bool verbose = True

    img_shape = img.shape
    cdef unsigned tau_2D_hard = BIOR
    cdef unsigned tau_2D_wien = DCT
    cdef unsigned color_space = RGB
    cdef unsigned height = img_shape[0]
    cdef unsigned width = img_shape[1]
    cdef unsigned chnls = img_shape[2]
    cdef int patch_size = 0
    cdef int nb_threads = 0

    print("Image of dimentions H*W*C={}*{}*{}"
          .format(height, width, chnls))
    img = img.transpose(2, 1, 0)
    print("Image tranposed of dimentions {}*{}*{}"
          .format(img.shape[0], img.shape[1], img.shape[2]))
    img = img.flatten()

    print("Running: "
          "./bm3d SomeInputImage -useSD_wien -tau_2d_hard bior -tau_2d_wien dct -color_space rgb")

    cdef np.ndarray img_vec = np.ascontiguousarray(img, dtype=np.float32) # Makes a contiguous copy of the numpy array.

    cdef vector[float] img_noisy = img_vec
    cdef vector[float] img_basic, img_denoised
    ret = run_bm3d(10, img_noisy, img_basic, img_denoised,
                   width, height, chnls, useSD_1, useSD_2,
                   tau_2D_hard, tau_2D_wien,
                   color_space, patch_size,
                   nb_threads, verbose)

    if ret != 0:
        raise Exception("Executing the C function `run_bmd3d` returned "
                        "with an error: {%d}".format(ret))

    cdef np.ndarray img_res = img_denoised
    img_res.reshape((img_shape[2], img_shape[1], img_shape[0])
                         ).transpose((2, 1, 0))

    res = img_denoised
    return res


