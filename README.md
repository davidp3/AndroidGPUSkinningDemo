This is a well documented and efficient example of shader-based skinning on Android.  It
was initially an educational tool for people afraid of the algebra.  The implementation
also tries to highlight some of the many choices inherent in implementing skinning that
are the reason that so many tutorials offer different math, seemingly without even
knowing it.  For example, back in the day I wrote a very coordinate-space-aware
spline-based animation compression tool for Gamebryo on PS3 and was able to reduce animation
sizes by over 90% by choosing spaces where quantization was effective... and using B-splines :)

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
But it's also a prety good implementation of HW skinning, if I do say so myself.  Good, robust,
fully working examples seem to be in very short supply.  I plan to beef up this non-code-based
description in the near future.

<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160703-094345.png" width="360"/>
<img src="https://raw.githubusercontent.com/davidp3/AndroidGPUSkinningDemo/master/etc/Screenshot_20160703-094419.png" width="360"/>
