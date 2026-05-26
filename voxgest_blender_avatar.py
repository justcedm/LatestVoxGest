"""
╔══════════════════════════════════════════════════════════════════╗
║  VoxGest Avatar Generator — Blender Python Script               ║
║  Version 1.0                                                     ║
║                                                                  ║
║  HOW TO RUN:                                                     ║
║    1. Open Blender 3.6+ or 4.x                                  ║
║    2. Go to Scripting workspace                                  ║
║    3. Open this file (or paste)                                  ║
║    4. Click ▶ Run Script                                         ║
║    5. GLB files are saved to ~/Desktop/VoxGest_GLB/             ║
║                                                                  ║
║  WHAT THIS CREATES:                                              ║
║    • voxgest_avatar.glb  — full avatar + all 5 animations        ║
║    • IDLE.glb            — breathing/neutral loop                ║
║    • HELLO.glb           — wave sign                             ║
║    • THANKYOU.glb        — thank-you sign                        ║
║    • WATER.glb           — water sign                            ║
║    • EAT.glb             — eat sign                              ║
║                                                                  ║
║  NOTE: This generates a rigged placeholder cartoon avatar.      ║
║  For final production, refine the mesh in Sculpt/Edit mode.     ║
╚══════════════════════════════════════════════════════════════════╝
"""

import bpy
import bmesh
import math
import os
from mathutils import Vector, Euler, Matrix

# ═══════════════════════════════════════════════════════════════════
# 0 — CONFIGURATION
# ═══════════════════════════════════════════════════════════════════

OUTPUT_DIR = os.path.join(os.path.expanduser("~"), "Desktop", "VoxGest_GLB")
FPS = 30

# Brand colors (linear sRGB — Blender uses linear, not gamma)
# Convert hex to linear: value^2.2 approx
C_TEAL_PRIMARY   = (0.016, 0.087, 0.094, 1.0)  # #0B3F43
C_TEAL_SECONDARY = (0.031, 0.138, 0.153, 1.0)  # #18686D
C_SKIN           = (0.766, 0.479, 0.306, 1.0)  # warm skin
C_SKIN_SHADOW    = (0.581, 0.295, 0.149, 1.0)  # darker skin
C_HAIR           = (0.020, 0.010, 0.005, 1.0)  # near-black brown
C_EYE_DARK       = (0.004, 0.004, 0.010, 1.0)  # pupil
C_EYE_IRIS       = (0.031, 0.138, 0.153, 1.0)  # teal iris
C_EYE_WHITE      = (0.890, 0.890, 0.875, 1.0)  # sclera
C_LIPS           = (0.617, 0.214, 0.149, 1.0)  # warm lip tone
C_BLUSH          = (0.830, 0.424, 0.340, 1.0)  # cheek blush


# ═══════════════════════════════════════════════════════════════════
# 1 — SCENE SETUP
# ═══════════════════════════════════════════════════════════════════

def clear_scene():
    """Remove everything from scene and data blocks."""
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for block_type in (
        bpy.data.meshes,
        bpy.data.materials,
        bpy.data.armatures,
        bpy.data.actions,
        bpy.data.curves,
    ):
        for item in list(block_type):
            block_type.remove(item)

clear_scene()
os.makedirs(OUTPUT_DIR, exist_ok=True)


# ═══════════════════════════════════════════════════════════════════
# 2 — MATERIAL FACTORY
# ═══════════════════════════════════════════════════════════════════

def make_material(name, base_color, roughness=0.85, metallic=0.0,
                  specular=0.3, emission=None):
    """Create a simple PBR material. base_color is (R,G,B,A) in linear sRGB."""
    m = bpy.data.materials.new(name=name)
    m.use_nodes = True
    tree = m.node_tree
    bsdf = tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = base_color
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    # Blender 3.x uses "Specular", 4.x uses "Specular IOR Level"
    for spec_key in ("Specular", "Specular IOR Level"):
        if spec_key in bsdf.inputs:
            bsdf.inputs[spec_key].default_value = specular
            break
    if emission:
        bsdf.inputs["Emission"].default_value = (*emission, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 0.3
    return m

# Build all materials up front
M = {
    "skin":      make_material("M_Skin",         C_SKIN,           roughness=0.80),
    "skin_sh":   make_material("M_SkinShadow",   C_SKIN_SHADOW,    roughness=0.85),
    "teal":      make_material("M_Teal",         C_TEAL_PRIMARY,   roughness=0.70),
    "teal2":     make_material("M_Teal2",        C_TEAL_SECONDARY, roughness=0.65),
    "hair":      make_material("M_Hair",         C_HAIR,           roughness=0.60),
    "eye_white": make_material("M_EyeWhite",     C_EYE_WHITE,      roughness=0.20, specular=0.8),
    "iris":      make_material("M_Iris",         C_EYE_IRIS,       roughness=0.10, specular=1.0),
    "pupil":     make_material("M_Pupil",        C_EYE_DARK,       roughness=0.05, specular=1.0),
    "lips":      make_material("M_Lips",         C_LIPS,           roughness=0.60),
    "blush":     make_material("M_Blush",        C_BLUSH,          roughness=1.00),
}


# ═══════════════════════════════════════════════════════════════════
# 3 — PRIMITIVE HELPERS
# ═══════════════════════════════════════════════════════════════════

def deselect_all():
    bpy.ops.object.select_all(action='DESELECT')

def active_obj():
    return bpy.context.active_object

def assign_mat(obj, mat_key):
    """Assign a material from the M dict to an object."""
    mat = M[mat_key]
    obj_mats = obj.data.materials
    if obj_mats:
        obj_mats[0] = mat
    else:
        obj_mats.append(mat)

def shade_smooth_obj(obj):
    """Set smooth shading on all polygons."""
    for poly in obj.data.polygons:
        poly.use_smooth = True

def add_sphere(name, loc, scale, mat_key, segs=24, rings=12):
    """Add a UV sphere at loc with scale and material."""
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=segs, ring_count=rings, location=loc
    )
    obj = active_obj()
    obj.name = name
    obj.scale = Vector(scale)
    assign_mat(obj, mat_key)
    shade_smooth_obj(obj)
    return obj

def add_cylinder(name, loc, scale, rot_xyz_deg, mat_key, verts=20):
    """Add a cylinder at loc, rotated, with material."""
    bpy.ops.mesh.primitive_cylinder_add(
        vertices=verts, radius=1.0, depth=1.0, location=loc
    )
    obj = active_obj()
    obj.name = name
    obj.scale = Vector(scale)
    obj.rotation_euler = Euler([math.radians(a) for a in rot_xyz_deg])
    assign_mat(obj, mat_key)
    shade_smooth_obj(obj)
    return obj

def add_torus(name, loc, scale, mat_key,
              major_r=1.0, minor_r=0.25, major_segs=24, minor_segs=12):
    bpy.ops.mesh.primitive_torus_add(
        location=loc,
        major_radius=major_r,
        minor_radius=minor_r,
        major_segments=major_segs,
        minor_segments=minor_segs,
    )
    obj = active_obj()
    obj.name = name
    obj.scale = Vector(scale)
    assign_mat(obj, mat_key)
    shade_smooth_obj(obj)
    return obj


# ═══════════════════════════════════════════════════════════════════
# 4 — BUILD AVATAR MESH
#
# Coordinate system (Blender Z-up, Y-back):
#   X+  = avatar's right arm (viewer's LEFT)
#   X-  = avatar's left arm  (viewer's RIGHT)
#   Z+  = up
#   Y-  = forward (toward viewer / camera)
#
# Avatar height: waist at Z=0, head top at Z≈2.0
# Only upper body is visible (waist upward)
# ═══════════════════════════════════════════════════════════════════

parts = []  # list of all mesh objects to join later


# ── TORSO ──────────────────────────────────────────────────────────
# Main shirt body — tapered cylinder
torso = add_cylinder("Torso", (0, 0, 0.60), (0.36, 0.22, 0.50), (0, 0, 0), "teal", verts=24)
parts.append(torso)

# Shoulder flare — wider at top of torso
sh_flare = add_cylinder("ShoulderFlare", (0, 0, 1.00), (0.42, 0.23, 0.08), (0, 0, 0), "teal", verts=24)
parts.append(sh_flare)

# Collar / neckline band
collar = add_cylinder("Collar", (0, -0.02, 1.10), (0.14, 0.12, 0.055), (4, 0, 0), "teal2", verts=20)
parts.append(collar)

# Waist area (slightly narrower)
waist = add_cylinder("Waist", (0, 0, 0.15), (0.32, 0.20, 0.20), (0, 0, 0), "teal", verts=20)
parts.append(waist)


# ── NECK ──────────────────────────────────────────────────────────
neck = add_cylinder("Neck", (0, -0.01, 1.20), (0.115, 0.105, 0.18), (2, 0, 0), "skin", verts=20)
parts.append(neck)


# ── HEAD ──────────────────────────────────────────────────────────
# Main head shape — slightly wider than tall
head = add_sphere("Head", (0, -0.02, 1.65), (0.42, 0.36, 0.43), "skin", segs=32, rings=16)
parts.append(head)

# Jaw / chin — pushed slightly forward and down
jaw = add_sphere("Jaw", (0, -0.06, 1.52), (0.34, 0.28, 0.22), "skin", segs=24, rings=12)
parts.append(jaw)

# Cheek fullness — cartoon-style puffy cheeks
cheek_r = add_sphere("CheekR",  (0.24, -0.26, 1.62), (0.16, 0.10, 0.14), "skin")
cheek_l = add_sphere("CheekL", (-0.24, -0.26, 1.62), (0.16, 0.10, 0.14), "skin")
parts.extend([cheek_r, cheek_l])


# ── HAIR ──────────────────────────────────────────────────────────
# Top cap — covers upper head
hair_top   = add_sphere("HairTop",   (0,    0.01, 1.76), (0.44, 0.37, 0.30), "hair", segs=28, rings=14)
# Back — shoulder-length hanging hair
hair_back  = add_sphere("HairBack",  (0,    0.26, 1.58), (0.40, 0.14, 0.32), "hair", segs=20, rings=10)
hair_back2 = add_sphere("HairBack2", (0,    0.30, 1.35), (0.36, 0.12, 0.24), "hair")
# Side curtains (shoulder length)
hair_sr    = add_sphere("HairSR",    ( 0.38, 0.08, 1.52), (0.13, 0.10, 0.34), "hair")
hair_sl    = add_sphere("HairSL",   (-0.38, 0.08, 1.52), (0.13, 0.10, 0.34), "hair")
# Front fringe / parted bangs
hair_bang_r = add_sphere("HairBangR",  ( 0.10, -0.28, 1.86), (0.20, 0.06, 0.07), "hair")
hair_bang_l = add_sphere("HairBangL", (-0.12, -0.28, 1.86), (0.18, 0.06, 0.07), "hair")
parts.extend([hair_top, hair_back, hair_back2, hair_sr, hair_sl, hair_bang_r, hair_bang_l])


# ── EYES ──────────────────────────────────────────────────────────
# Cartoon-large eyes — X offset, Y-forward (negative Y), Z height
EX =  0.155   # x offset
EY = -0.330   # y (forward)
EZ =  1.720   # z height

for side, sx in (("R", EX), ("L", -EX)):
    # Sclera (white of eye) — slightly flattened sphere
    ew = add_sphere(f"EyeWhite_{side}", (sx, EY,       EZ), (0.140, 0.058, 0.140), "eye_white", segs=20, rings=10)
    # Iris — teal, slightly in front
    ei = add_sphere(f"Iris_{side}",    (sx, EY-0.030, EZ), (0.090, 0.038, 0.090), "iris",      segs=16, rings=8)
    # Pupil — dark center
    ep = add_sphere(f"Pupil_{side}",   (sx, EY-0.042, EZ), (0.050, 0.025, 0.050), "pupil",     segs=12, rings=6)
    # Highlight spec dot (white)
    ehi = add_sphere(f"EyeHi_{side}",  (sx+0.025, EY-0.045, EZ+0.030), (0.018, 0.010, 0.018), "eye_white", segs=8, rings=4)
    parts.extend([ew, ei, ep, ehi])

# Upper eyelids — skin-tone curves over eyes
lid_r = add_sphere("EyelidR",  ( EX,  EY-0.010, EZ+0.065), (0.155, 0.025, 0.058), "skin")
lid_l = add_sphere("EyelidL", (-EX,  EY-0.010, EZ+0.065), (0.155, 0.025, 0.058), "skin")
parts.extend([lid_r, lid_l])

# Lower eyelid (subtle)
llid_r = add_sphere("LowerLidR",  ( EX,  EY-0.010, EZ-0.065), (0.130, 0.020, 0.040), "skin")
llid_l = add_sphere("LowerLidL", (-EX,  EY-0.010, EZ-0.065), (0.130, 0.020, 0.040), "skin")
parts.extend([llid_r, llid_l])

# Eyebrows — arched, dark
brow_r = add_sphere("BrowR",  ( EX+0.010, -0.310, EZ+0.095), (0.130, 0.022, 0.028), "hair")
brow_l = add_sphere("BrowL", (-EX-0.010, -0.310, EZ+0.095), (0.130, 0.022, 0.028), "hair")
parts.extend([brow_r, brow_l])

# Blush (very subtle, transparent-looking via color)
blush_r = add_sphere("BlushR",  ( 0.240, -0.290, EZ-0.045), (0.110, 0.020, 0.090), "blush")
blush_l = add_sphere("BlushL", (-0.240, -0.290, EZ-0.045), (0.110, 0.020, 0.090), "blush")
parts.extend([blush_r, blush_l])


# ── NOSE ──────────────────────────────────────────────────────────
nose      = add_sphere("Nose",     (0,     -0.400, 1.620), (0.042, 0.036, 0.042), "skin")
nose_tip  = add_sphere("NoseTip",  (0,     -0.415, 1.600), (0.038, 0.028, 0.035), "skin")
nose_r    = add_sphere("NoseWingR",  ( 0.048, -0.390, 1.598), (0.028, 0.020, 0.026), "skin")
nose_l    = add_sphere("NoseWingL", (-0.048, -0.390, 1.598), (0.028, 0.020, 0.026), "skin")
parts.extend([nose, nose_tip, nose_r, nose_l])


# ── MOUTH / LIPS ──────────────────────────────────────────────────
lip_top    = add_sphere("LipTop",    (0, -0.415, 1.558), (0.115, 0.022, 0.042), "lips")
lip_bottom = add_sphere("LipBottom", (0, -0.412, 1.528), (0.100, 0.020, 0.035), "lips")
# Cupid's bow bump
cupid = add_sphere("Cupid", (0, -0.420, 1.570), (0.040, 0.018, 0.020), "lips")
# Corner lip bulges
lip_cr = add_sphere("LipCornerR",  ( 0.100, -0.408, 1.545), (0.028, 0.018, 0.028), "lips")
lip_cl = add_sphere("LipCornerL", (-0.100, -0.408, 1.545), (0.028, 0.018, 0.028), "lips")
parts.extend([lip_top, lip_bottom, cupid, lip_cr, lip_cl])


# ── SHOULDERS ─────────────────────────────────────────────────────
sh_r = add_sphere("ShoulderR",  ( 0.42, 0, 0.98), (0.19, 0.14, 0.20), "teal")
sh_l = add_sphere("ShoulderL", (-0.42, 0, 0.98), (0.19, 0.14, 0.20), "teal")
parts.extend([sh_r, sh_l])


# ── UPPER ARMS ────────────────────────────────────────────────────
# Cylinders rotated 90° around Y — running along X axis
ua_r = add_cylinder("UpperArmR",  ( 0.60, 0, 0.90), (0.115, 0.115, 0.34), (0, 90, 0), "teal",  verts=20)
ua_l = add_cylinder("UpperArmL", (-0.60, 0, 0.90), (0.115, 0.115, 0.34), (0, 90, 0), "teal",  verts=20)
parts.extend([ua_r, ua_l])

# Elbow caps (small spheres at elbow joint)
elbow_r = add_sphere("ElbowCapR",  ( 0.85, 0, 0.90), (0.120, 0.100, 0.120), "skin")
elbow_l = add_sphere("ElbowCapL", (-0.85, 0, 0.90), (0.120, 0.100, 0.120), "skin")
parts.extend([elbow_r, elbow_l])


# ── FOREARMS ──────────────────────────────────────────────────────
fa_r = add_cylinder("ForearmR",  ( 1.00, 0, 0.90), (0.100, 0.100, 0.30), (0, 90, 0), "skin", verts=20)
fa_l = add_cylinder("ForearmL", (-1.00, 0, 0.90), (0.100, 0.100, 0.30), (0, 90, 0), "skin", verts=20)
parts.extend([fa_r, fa_l])

# Wrist caps
wr_r = add_sphere("WristCapR",  ( 1.18, 0, 0.90), (0.105, 0.092, 0.105), "skin")
wr_l = add_sphere("WristCapL", (-1.18, 0, 0.90), (0.105, 0.092, 0.105), "skin")
parts.extend([wr_r, wr_l])


# ── HANDS ─────────────────────────────────────────────────────────
# Palm (slightly flattened sphere)
palm_r = add_sphere("PalmR",  ( 1.30, 0, 0.90), (0.17, 0.075, 0.155), "skin", segs=20, rings=10)
palm_l = add_sphere("PalmL", (-1.30, 0, 0.90), (0.17, 0.075, 0.155), "skin", segs=20, rings=10)
parts.extend([palm_r, palm_l])

# Fingers — 4 rounded cylinders per hand, fanned in Z
# Finger offsets from palm center (dz for fan, dx for length)
FINGER_LAYOUT = [
    ("Index",  0.070,  0.090),  # z_offset, length_x
    ("Middle", 0.025,  0.098),
    ("Ring",  -0.025,  0.090),
    ("Pinky", -0.065,  0.075),
]

for fname, dz, flen in FINGER_LAYOUT:
    for side, sx in (("R", 1), ("L", -1)):
        fx = sx * (1.38 + flen * 0.5)
        fz = 0.90 + dz
        f_base = add_cylinder(
            f"Finger{fname}{side[0]}",
            (fx, 0, fz),
            (0.040, 0.038, flen),
            (0, 90, 0),
            "skin", verts=12
        )
        # Rounded fingertip cap
        f_tip = add_sphere(
            f"FingerTip{fname}{side[0]}",
            (sx * (1.38 + flen), 0, fz),
            (0.042, 0.036, 0.044),
            "skin", segs=10, rings=6
        )
        f_knuckle = add_sphere(
            f"Knuckle{fname}{side[0]}",
            (sx * 1.22, 0, fz),
            (0.048, 0.040, 0.045),
            "skin", segs=10, rings=6
        )
        parts.extend([f_base, f_tip, f_knuckle])

# Thumbs — angled slightly downward
thumb_r = add_cylinder("ThumbR",  ( 1.22, -0.020, 0.835), (0.046, 0.042, 0.085), (0, 70, -25), "skin", verts=12)
thumb_l = add_cylinder("ThumbL", (-1.22, -0.020, 0.835), (0.046, 0.042, 0.085), (0, 70,  25), "skin", verts=12)
thumb_tip_r = add_sphere("ThumbTipR",  ( 1.30, -0.025, 0.805), (0.050, 0.042, 0.052), "skin", segs=10, rings=6)
thumb_tip_l = add_sphere("ThumbTipL", (-1.30, -0.025, 0.805), (0.050, 0.042, 0.052), "skin", segs=10, rings=6)
parts.extend([thumb_r, thumb_l, thumb_tip_r, thumb_tip_l])


# ═══════════════════════════════════════════════════════════════════
# 5 — JOIN ALL PARTS INTO ONE MESH
# ═══════════════════════════════════════════════════════════════════

print(f"[VoxGest] Joining {len(parts)} mesh parts...")

deselect_all()
for p in parts:
    p.select_set(True)
bpy.context.view_layer.objects.active = torso  # active = join target
bpy.ops.object.join()

avatar_mesh = active_obj()
avatar_mesh.name = "VoxGest_Avatar"

# Apply transforms so the rig lines up
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)

print("[VoxGest] Mesh joined and transforms applied.")


# ═══════════════════════════════════════════════════════════════════
# 6 — ARMATURE
# ═══════════════════════════════════════════════════════════════════

print("[VoxGest] Building armature...")

deselect_all()
bpy.ops.object.armature_add(location=(0, 0, 0))
arm_obj = active_obj()
arm_obj.name = "VoxGest_Rig"
arm_data = arm_obj.data
arm_data.name = "VoxGest_Rig"
arm_obj.show_in_front = True

bpy.ops.object.mode_set(mode='EDIT')
eb = arm_data.edit_bones

# Remove the default bone added by armature_add
for b in list(eb):
    eb.remove(b)

def add_bone(name, head_pos, tail_pos, parent_name=None, roll_deg=0.0):
    """
    Create an edit bone.
    head_pos, tail_pos: (x, y, z) tuples
    parent_name: string name of parent bone or None
    """
    b = eb.new(name)
    b.head = Vector(head_pos)
    b.tail = Vector(tail_pos)
    b.roll = math.radians(roll_deg)
    if parent_name and parent_name in eb:
        b.parent = eb[parent_name]
        b.use_connect = False
    return b

# ── Spine chain ────────────────────────────────────────────────────
add_bone("Root",   (0, 0, 0.00), (0, 0, 0.12))
add_bone("Hips",   (0, 0, 0.12), (0, 0, 0.35), "Root")
add_bone("Spine",  (0, 0, 0.35), (0, 0, 0.75), "Hips")
add_bone("Chest",  (0, 0, 0.75), (0, 0, 1.08), "Spine")
add_bone("Neck",   (0, 0, 1.08), (0, 0, 1.32), "Chest")
add_bone("Head",   (0, 0, 1.32), (0, 0, 1.90), "Neck")

# ── Right arm (positive X = avatar's right) ───────────────────────
add_bone("Clavicle_R", ( 0.10, 0, 1.02), ( 0.28, 0, 1.00), "Chest")
add_bone("UpperArm_R", ( 0.28, 0, 1.00), ( 0.60, 0, 0.90), "Clavicle_R")
add_bone("Forearm_R",  ( 0.60, 0, 0.90), ( 0.92, 0, 0.90), "UpperArm_R")
add_bone("Hand_R",     ( 0.92, 0, 0.90), ( 1.14, 0, 0.90), "Forearm_R")

# Right hand fingers (approximate positions)
add_bone("Index_R",    ( 1.14, 0, 0.940), ( 1.30, 0, 0.950), "Hand_R")
add_bone("Middle_R",   ( 1.14, 0, 0.900), ( 1.32, 0, 0.900), "Hand_R")
add_bone("Ring_R",     ( 1.14, 0, 0.858), ( 1.30, 0, 0.855), "Hand_R")
add_bone("Pinky_R",    ( 1.14, 0, 0.820), ( 1.28, 0, 0.818), "Hand_R")
add_bone("Thumb_R",    ( 1.08, 0, 0.855), ( 1.20,-0.02, 0.810), "Hand_R")

# ── Left arm (negative X = avatar's left) ────────────────────────
add_bone("Clavicle_L", (-0.10, 0, 1.02), (-0.28, 0, 1.00), "Chest")
add_bone("UpperArm_L", (-0.28, 0, 1.00), (-0.60, 0, 0.90), "Clavicle_L")
add_bone("Forearm_L",  (-0.60, 0, 0.90), (-0.92, 0, 0.90), "UpperArm_L")
add_bone("Hand_L",     (-0.92, 0, 0.90), (-1.14, 0, 0.90), "Forearm_L")

# Left hand fingers
add_bone("Index_L",    (-1.14, 0, 0.940), (-1.30, 0, 0.950), "Hand_L")
add_bone("Middle_L",   (-1.14, 0, 0.900), (-1.32, 0, 0.900), "Hand_L")
add_bone("Ring_L",     (-1.14, 0, 0.858), (-1.30, 0, 0.855), "Hand_L")
add_bone("Pinky_L",    (-1.14, 0, 0.820), (-1.28, 0, 0.818), "Hand_L")
add_bone("Thumb_L",    (-1.08, 0, 0.855), (-1.20,-0.02, 0.810), "Hand_L")

bpy.ops.object.mode_set(mode='OBJECT')
print("[VoxGest] Armature built.")


# ═══════════════════════════════════════════════════════════════════
# 7 — PARENT MESH TO ARMATURE (automatic weights)
# ═══════════════════════════════════════════════════════════════════

print("[VoxGest] Parenting mesh to armature (auto weights)...")

deselect_all()
avatar_mesh.select_set(True)
arm_obj.select_set(True)
bpy.context.view_layer.objects.active = arm_obj
bpy.ops.object.parent_set(type='ARMATURE_AUTO')

print("[VoxGest] Skinning complete.")


# ═══════════════════════════════════════════════════════════════════
# 8 — ANIMATION HELPERS
# ═══════════════════════════════════════════════════════════════════

def get_pb(bone_name):
    """Get pose bone by name."""
    return arm_obj.pose.bones[bone_name]

def kf_rot(pose_bone, frame, xyz_deg, mode='XYZ'):
    """Keyframe a rotation in degrees."""
    pose_bone.rotation_mode = mode
    pose_bone.rotation_euler = Euler([math.radians(x) for x in xyz_deg])
    pose_bone.keyframe_insert(data_path="rotation_euler", frame=frame)

def kf_loc(pose_bone, frame, xyz):
    """Keyframe a local location offset."""
    pose_bone.location = Vector(xyz)
    pose_bone.keyframe_insert(data_path="location", frame=frame)

def smooth_all_fcurves(action):
    """
    Set keyframe handles to smooth easing when Blender exposes fcurves.

    Blender 5.x changed the Action API, so action.fcurves may not exist.
    If fcurves are unavailable, safely skip smoothing instead of crashing.
    The animation will still export and work.
    """
    fcurves = getattr(action, "fcurves", None)

    if fcurves is None:
        print(f"[VoxGest] Warning: Cannot access fcurves for {action.name}; skipping curve smoothing.")
        return

    for fc in fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = 'BEZIER'
            kp.handle_left_type = 'AUTO_CLAMPED'
            kp.handle_right_type = 'AUTO_CLAMPED'

def new_action(name):
    """Create a blank action and assign it to the armature."""
    action = bpy.data.actions.new(name=name)
    arm_obj.animation_data_create()
    arm_obj.animation_data.action = action
    return action

def reset_bone_rot(pose_bone, frame):
    """Keyframe a bone at zero rotation on given frame."""
    kf_rot(pose_bone, frame, [0, 0, 0])

def push_to_nla(action, track_name, start_frame=1):
    """Push action into an NLA track strip."""
    ad = arm_obj.animation_data
    track = ad.nla_tracks.new()
    track.name = track_name
    strip = track.strips.new(track_name, start_frame, action)
    strip.name = track_name
    strip.action = action
    return strip


# ═══════════════════════════════════════════════════════════════════
# 9 — BUILD ANIMATIONS
# ═══════════════════════════════════════════════════════════════════

print("[VoxGest] Creating animations...")

# ── References to frequently used pose bones ──
PB = {name: get_pb(name) for name in [
    "Root", "Hips", "Spine", "Chest", "Neck", "Head",
    "Clavicle_R", "UpperArm_R", "Forearm_R", "Hand_R",
    "Index_R", "Middle_R", "Ring_R", "Pinky_R", "Thumb_R",
    "Clavicle_L", "UpperArm_L", "Forearm_L", "Hand_L",
    "Index_L", "Middle_L", "Ring_L", "Pinky_L", "Thumb_L",
]}


# ────────────────────────────────────────────────────────────────────
# IDLE  (60 frames, seamless loop at 30fps = 2s)
# Subtle breathing via Chest, gentle head sway.
# Left arm is near chest (reference image pose).
# ────────────────────────────────────────────────────────────────────
def build_idle():
    act = new_action("IDLE")

    # Breathing: Chest rocks gently on X axis
    for f, rx in [(1, 0.0), (12, 1.4), (30, 0.0), (48, -0.3), (60, 0.0)]:
        kf_rot(PB["Chest"], f, [rx, 0, 0])

    # Spine follows chest subtly
    for f, rx in [(1, 0.0), (12, 0.5), (30, 0.0), (48, -0.1), (60, 0.0)]:
        kf_rot(PB["Spine"], f, [rx, 0, 0])

    # Head: gentle tilt left/right (Y axis in local)
    for f, ry in [(1, 0.0), (20, 0.7), (40, -0.5), (60, 0.0)]:
        kf_rot(PB["Head"], f, [0, ry, 0])

    # Right arm: relaxed at side, slight outward drape
    for f in [1, 60]:
        kf_rot(PB["UpperArm_R"], f, [ 5,  0,  18])  # slight forward + out
        kf_rot(PB["Forearm_R"],  f, [ 0,  0,   6])
        kf_rot(PB["Hand_R"],     f, [ 0,  0,   0])

    # Left arm: near chest (matches reference LISTEN screen pose)
    for f in [1, 60]:
        kf_rot(PB["UpperArm_L"], f, [-22,  0, -28])
        kf_rot(PB["Forearm_L"],  f, [-35,  0,   0])
        kf_rot(PB["Hand_L"],     f, [  8,  0,  12])

    smooth_all_fcurves(act)
    act.frame_range = (1, 60)
    act.use_cyclic = True   # mark as looping in NLA
    print("[VoxGest] ✓ IDLE animation built (60 frames, loop)")
    return act


# ────────────────────────────────────────────────────────────────────
# HELLO  (36 frames ≈ 1.2s)
# Right hand raises to head height, palm open, two wrist waves.
# ────────────────────────────────────────────────────────────────────
def build_hello():
    act = new_action("HELLO")

    # F1 — Neutral start
    kf_rot(PB["UpperArm_R"], 1, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  1, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     1, [ 0,  0,  0])

    # F10 — Arm raises to head level, elbow bent outward
    kf_rot(PB["UpperArm_R"], 10, [-68,  0, 28])
    kf_rot(PB["Forearm_R"],  10, [ -8,  0, 22])
    kf_rot(PB["Hand_R"],     10, [  0, -8,  0])   # palm faces viewer

    # F18 — First wave: wrist bends right (avatar's right)
    kf_rot(PB["UpperArm_R"], 18, [-70,  0, 22])
    kf_rot(PB["Hand_R"],     18, [  0, -28, 0])

    # F27 — Second wave: wrist bends left
    kf_rot(PB["UpperArm_R"], 27, [-68,  0, 32])
    kf_rot(PB["Hand_R"],     27, [  0,  22, 0])

    # F36 — Return to neutral
    kf_rot(PB["UpperArm_R"], 36, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  36, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     36, [ 0,  0,  0])

    smooth_all_fcurves(act)
    act.frame_range = (1, 36)
    print("[VoxGest] ✓ HELLO animation built (36 frames)")
    return act


# ────────────────────────────────────────────────────────────────────
# THANKYOU  (30 frames ≈ 1.0s)
# Right hand flat near chin, pushes forward and down.
# Approximate ASL THANK-YOU / gratitude gesture.
# ────────────────────────────────────────────────────────────────────
def build_thankyou():
    act = new_action("THANKYOU")

    # F1 — Neutral
    kf_rot(PB["UpperArm_R"], 1, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  1, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     1, [ 0,  0,  0])

    # F8 — Hand raised to chin/lip level, palm facing avatar's face
    kf_rot(PB["UpperArm_R"],  8, [-48,  0, 32])
    kf_rot(PB["Forearm_R"],   8, [-28,  0, 10])
    kf_rot(PB["Hand_R"],      8, [ 10,  0,  0])   # wrist cocks back slightly

    # F20 — Push forward: arm extends outward and down (the TY arc)
    kf_rot(PB["UpperArm_R"], 20, [-32,  0, 42])
    kf_rot(PB["Forearm_R"],  20, [ -4,  0,  5])
    kf_rot(PB["Hand_R"],     20, [  4,  0,  0])

    # F30 — Return neutral
    kf_rot(PB["UpperArm_R"], 30, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  30, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     30, [ 0,  0,  0])

    smooth_all_fcurves(act)
    act.frame_range = (1, 30)
    print("[VoxGest] ✓ THANKYOU animation built (30 frames)")
    return act


# ────────────────────────────────────────────────────────────────────
# WATER  (35 frames ≈ 1.17s)
# W-handshape (3 fingers raised) near chin, 3 small tapping bounces.
# ────────────────────────────────────────────────────────────────────
def build_water():
    act = new_action("WATER")

    # F1 — Neutral
    kf_rot(PB["UpperArm_R"], 1, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  1, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     1, [ 0,  0,  0])
    kf_rot(PB["Ring_R"],     1, [ 0,  0,  0])   # ring finger tucked for W-shape
    kf_rot(PB["Pinky_R"],    1, [ 0,  0,  0])

    # F8 — Raise to chin, W-shape (ring + pinky tucked)
    kf_rot(PB["UpperArm_R"],  8, [-54,  0, 28])
    kf_rot(PB["Forearm_R"],   8, [-18,  0,  8])
    kf_rot(PB["Hand_R"],      8, [  0,  0,  0])
    kf_rot(PB["Ring_R"],      8, [ 60,  0,  0])  # tucked
    kf_rot(PB["Pinky_R"],     8, [ 55,  0,  0])  # tucked

    # 3 tapping bounces: frames 13, 18, 23
    for tap_f, tap_rx in [(13, -4), (18, 3), (23, -5)]:
        kf_rot(PB["UpperArm_R"], tap_f, [-54 + tap_rx, 0, 28])
        kf_rot(PB["Forearm_R"],  tap_f, [-18 + tap_rx * 0.6, 0, 8])

    # F35 — Return neutral, release finger tuck
    kf_rot(PB["UpperArm_R"], 35, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  35, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     35, [ 0,  0,  0])
    kf_rot(PB["Ring_R"],     35, [ 0,  0,  0])
    kf_rot(PB["Pinky_R"],    35, [ 0,  0,  0])

    smooth_all_fcurves(act)
    act.frame_range = (1, 35)
    print("[VoxGest] ✓ WATER animation built (35 frames)")
    return act


# ────────────────────────────────────────────────────────────────────
# EAT  (35 frames ≈ 1.17s)
# Flat O-handshape: hand moves toward mouth twice.
# ────────────────────────────────────────────────────────────────────
def build_eat():
    act = new_action("EAT")

    # F1 — Neutral
    kf_rot(PB["UpperArm_R"], 1, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  1, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     1, [ 0,  0,  0])

    # F8 — Hand near mouth, fingers rounded (O-shape)
    kf_rot(PB["UpperArm_R"],  8, [-58,  0, 30])
    kf_rot(PB["Forearm_R"],   8, [-35,  0,  8])
    kf_rot(PB["Hand_R"],      8, [  0,  0,  0])
    # Curl fingers for O-shape
    for finger in ["Index_R", "Middle_R", "Ring_R", "Pinky_R"]:
        kf_rot(PB[finger], 8, [40, 0, 0])

    # F14 — Move toward mouth (forearm extends inward)
    kf_rot(PB["Forearm_R"],  14, [-50,  0,  8])

    # F20 — Pull back
    kf_rot(PB["Forearm_R"],  20, [-30,  0,  8])

    # F26 — Second toward mouth
    kf_rot(PB["Forearm_R"],  26, [-52,  0,  8])

    # F35 — Return neutral, release finger curl
    kf_rot(PB["UpperArm_R"], 35, [ 5,  0, 18])
    kf_rot(PB["Forearm_R"],  35, [ 0,  0,  6])
    kf_rot(PB["Hand_R"],     35, [ 0,  0,  0])
    for finger in ["Index_R", "Middle_R", "Ring_R", "Pinky_R"]:
        kf_rot(PB[finger], 35, [0, 0, 0])

    smooth_all_fcurves(act)
    act.frame_range = (1, 35)
    print("[VoxGest] ✓ EAT animation built (35 frames)")
    return act


# Run all builders
action_idle     = build_idle()
action_hello    = build_hello()
action_thankyou = build_thankyou()
action_water    = build_water()
action_eat      = build_eat()

ANIMATIONS = [
    (action_idle,     "IDLE"),
    (action_hello,    "HELLO"),
    (action_thankyou, "THANKYOU"),
    (action_water,    "WATER"),
    (action_eat,      "EAT"),
]


# ═══════════════════════════════════════════════════════════════════
# 10 — PUSH ALL ACTIONS TO NLA TRACKS
# NLA tracks allow all animations to be embedded in one GLB.
# ═══════════════════════════════════════════════════════════════════

print("[VoxGest] Pushing to NLA...")

# Clear active action so NLA strips take over
arm_obj.animation_data.action = None

for act, track_name in ANIMATIONS:
    push_to_nla(act, track_name, start_frame=1)
    print(f"[VoxGest]   NLA track: {track_name}")


# ═══════════════════════════════════════════════════════════════════
# 11 — EXPORT GLB FILES
# ═══════════════════════════════════════════════════════════════════

print(f"\n[VoxGest] Exporting to: {OUTPUT_DIR}\n")

def export_glb(filepath, use_nla=True, single_action=None):
    """
    Export the current scene as GLB.
    use_nla=True → exports all NLA strips as separate animations
    single_action → if set, exports only that action as current action
    """
    if single_action is not None:
        arm_obj.animation_data.action = single_action

    # Select everything (mesh + armature)
    bpy.ops.object.select_all(action='SELECT')

    kwargs = dict(
        filepath=filepath,
        export_format='GLB',
        use_selection=False,

        # Mesh
        export_apply=True,          # apply all modifiers
        export_normals=True,

        # Materials
        export_materials='EXPORT',
        export_colors=True,

        # Animations
        export_animations=True,
        export_nla_strips=use_nla,
        export_action_filter=False,

        # Optimization (no Draco for max compatibility; enable for production)
        export_draco_mesh_compression_enable=False,

        # Skinning
        export_skins=True,
        export_morph=True,          # blendshapes if any

        # Camera / lights (not needed)
        export_cameras=False,
        export_lights=False,
    )

    try:
        bpy.ops.export_scene.gltf(**kwargs)
        size_kb = os.path.getsize(filepath) // 1024
        print(f"  ✓ {os.path.basename(filepath)}  ({size_kb} KB)")
    except Exception as e:
        # Older Blender versions have slightly different parameter names
        # Retry with minimal safe set
        safe_kwargs = dict(
            filepath=filepath,
            export_format='GLB',
            export_animations=True,
            export_nla_strips=use_nla,
            export_apply=True,
        )
        bpy.ops.export_scene.gltf(**safe_kwargs)
        size_kb = os.path.getsize(filepath) // 1024
        print(f"  ✓ {os.path.basename(filepath)}  ({size_kb} KB)  [compat mode]")

    # Reset
    if single_action is not None:
        arm_obj.animation_data.action = None


# ── Main avatar GLB (contains ALL animations via NLA) ─────────────
main_glb = os.path.join(OUTPUT_DIR, "voxgest_avatar.glb")
print("Exporting main avatar (all animations embedded)...")
export_glb(main_glb, use_nla=True)


# ── Individual animation GLBs ──────────────────────────────────────
print("\nExporting individual animation GLBs...")
for act, anim_name in ANIMATIONS:
    path = os.path.join(OUTPUT_DIR, f"{anim_name}.glb")
    export_glb(path, use_nla=False, single_action=act)

arm_obj.animation_data.action = None


# ═══════════════════════════════════════════════════════════════════
# 12 — ANDROID ASSET COPY INSTRUCTIONS
# ═══════════════════════════════════════════════════════════════════

android_paths = {
    "voxgest_avatar.glb": "app/src/main/assets/avatar/models/voxgest_avatar.glb",
    "IDLE.glb":           "app/src/main/assets/avatar/animations/IDLE.glb",
    "HELLO.glb":          "app/src/main/assets/avatar/animations/HELLO.glb",
    "THANKYOU.glb":       "app/src/main/assets/avatar/animations/THANKYOU.glb",
    "WATER.glb":          "app/src/main/assets/avatar/animations/WATER.glb",
    "EAT.glb":            "app/src/main/assets/avatar/animations/EAT.glb",
}

print("\n" + "═" * 60)
print("✅  VoxGest avatar export complete!")
print("═" * 60)
print(f"\nFiles saved to: {OUTPUT_DIR}")
print("\nCopy to Android project:")
for src, dst in android_paths.items():
    print(f"  {src}  →  {dst}")

print("""
Next steps:
  1. Verify GLBs at https://gltf.report  (check animation names)
  2. Refine mesh in Sculpt mode for final look
  3. Weight paint UpperArm_R and Hand_R for signing clarity
  4. Enable Draco compression for production build
     (re-export with export_draco_mesh_compression_enable=True)

Android integration (SceneView):
  val viewer = ARSceneView(context)
  viewer.loadModelGlbAsync("avatar/models/voxgest_avatar.glb") { model ->
      model.playAnimation("IDLE", repeat = true)
  }
""")
