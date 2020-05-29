import numpy as np
from skimage import data, io
from skimage.metrics import peak_signal_noise_ratio

import bm3d_pyshell


noise_std_dev = 10.0
img = data.astronaut()

print("Adding noise")
noise = np.random.normal(scale=noise_std_dev,
                         size=img.shape).astype(img.dtype)
noisy_img = img + noise
print("Denoising with BM3D")
out = bm3d_pyshell.simple_bm3d(noisy_img)
noise_psnr = peak_signal_noise_ratio(img, noisy_img)
out_psnr = peak_signal_noise_ratio(img, out)

print("PSNR of noisy image: ", noise_psnr)
print("PSNR of reconstructed image: ", out_psnr)
io.imshow(noisy_img)
io.show()
io.imshow(out)
io.show()
