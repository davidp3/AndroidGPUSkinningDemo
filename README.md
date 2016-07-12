This is a well documented and efficient example of shader-based skinning on Android.  It
demonstrates the three most-used methods of shader-based transformation blending for
skinned characters: linear-(matrix) blending, quaternion blending and dual-quaternion
blending.  Dual Quaternions are quite new so they are not widely known but they are
clearly defined in [this SIGGRAPH paper](https://www.cs.utah.edu/~ladislav/kavan07skinning/kavan07skinning.pdf).
In a nutshell, they are quaternions that are also capable of expressing translations.  They
solve a lot of deformation problems produced by other forms of blending.

To compare, say, matrices with dual quaternions, you can look at 
`MatrixBones.java` and `vert_shader_matrix.glsl` for matrices and 
`DualQuatBones.java` and `vert_shader_dualquat.glsl` for dual quaternions.
You will find that the difference is maybe two dozen lines of real code.
  
This app was initially an educational tool for people afraid of the algebra.  It handles the Ogre and
ms3d (Milkshape) export formats.  The implementation
also tries to highlight some of the many choices inherent in implementing skinning that
are the reason that so many tutorials offer differing math, assuming they are discussing
the same thing.  These choices can matter -- for example, I once wrote a very coordinate-space-aware
spline-based animation compression tool for Gamebryo on PS3 and was able to reduce animation
sizes by over 90% by choosing spaces where quantization was effective... and using B-splines.

I present the math in a _units_-like manner as that's the simplest way I know how.  The general
idea is that transformations map from one coordinate system to another and those coordinate
systems can be viewed as units, as you think of them in physics.  For example, the bind pose
matrix has _"units"_ of `model/bone` because it maps from bone space to model space,
the same way velocity might have units of `cm/sec`.  When you multiply a velocity of `cm/sec`
by a time in `sec` then the `sec` units _cancel_, giving you a distance in `cm`.  Similarly,
when you multiply a vector in bone coordinates by the bind pose, you convert it to model
coordinates.  The skinning formula also uses the _inverse_ bind pose, which (you guessed it)
has units of `bone/model`, the inverse of the bind pose's units.  And when you compose them,
you get a unitless transformation, appropriate for mapping from model coordinates back into
model coordinates.  This is the essense of skinning method.

I'm using this code to demystify hardware skinning for non-mathematically-inclined engineers.
But it's also a prety good implementation of HW skinning.  Good, robust,
fully working examples seem to be in very short supply.  I plan to beef up this non-code-based
description in the near future.

You can find the free Ninja model I'm using [here](http://tf3dm.com/3d-model/ninja-48864.html) and the 
free Halo-like model [here](http://voxels.blogspot.com/2014/03/skinned-skeletal-animation-tutorial.html).

<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160712-043626.png" width="360"/>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160712-043657.png" width="360"/>
<br>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160703-094345.png" width="360"/>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160703-094419.png" width="360"/>
<br>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160706-120353.png" width="360"/>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160706-120436.png" width="360"/>
