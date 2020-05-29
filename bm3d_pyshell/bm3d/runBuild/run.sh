cd ../build/
make
mv bm3d ../runBuild/
cd ../runBuild
./bm3d astronaut_noisy.png 20 astronaut_denoised.png -useSD_wien -tau_2d_hard bior -tau_2d_wien dct -color_space rgb -nb_threads 0 -verbose
