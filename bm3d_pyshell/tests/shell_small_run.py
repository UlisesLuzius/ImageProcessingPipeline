import numpy as np
from skimage import data, measure, io

import bm3d_pyshell


noise_std_dev = 10.0
img = data.astronaut()
io.imshow(img)
print(type(img))
io.show()
print("Adding noise")
noise = np.random.normal(scale=noise_std_dev,
                         size=img.shape).astype(img.dtype)

noisy_img = img + noise
io.imshow(noisy_img)
io.show()
print("Denoising with BM3D")
out = bm3d_pyshell.simple_bm3d(noisy_img)

io.imshow(out)
io.show()

noise_psnr = measure.compare_psnr(img, noisy_img)
out_psnr = measure.compare_psnr(img, out)

print("PSNR of noisy image: ", noise_psnr)
print("PSNR of reconstructed image: ", out_psnr)
