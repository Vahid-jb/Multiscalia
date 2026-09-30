#!/usr/bin/env python3
"""
Periodic single polymer chains along x for efield_wave.in (LAMMPS, atom_style charge).

  PP : syndiotactic polypropylene, planar zigzag           -> PP_chain.data (types 1 C, 2 H)
  PC : bisphenol-A polycarbonate  -[O-C6H4-C(CH3)2-C6H4-O-C(=O)]-   (CAS 25037-45-0)
       repeat unit C16H14O3 (254.3 g/mol; the C16H18O5 often listed is bisphenol A +
       carbonic acid before the 2 H2O are condensed out), extended 2_1 helix,
       carbonate trans,trans                              -> PC_chain.data (types 1 C, 2 H, 3 O)
       Ring twists / backbone dihedrals default to the lowest-energy 2_1 conformer found
       with ffield.reax.cho after QEq minimisation.

The last repeat unit is bonded to the first one through the periodic x-boundary, so the
chain is infinite. Geometry is ideal (textbook bond lengths/angles): always minimise first
(efield_wave.in does).

usage:
  python3 make_chain.py PP  [-n 20] [-o OUTDIR]     # n = monomers, even (default 20)
  python3 make_chain.py PC  [-n 6]  [-o OUTDIR]     # n = repeat units, even (default 6)
"""
import argparse
import os
import sys
import numpy as np

WORKDIR = "./mexican_wave"   # default output dir
MASS = {"C": 12.011, "H": 1.008, "O": 15.999}
deg = np.pi / 180.0


def unit(v):
    return v / np.linalg.norm(v)


def perp(v, ax):
    """unit vector of the part of v perpendicular to the unit axis ax"""
    return unit(v - v.dot(ax) * ax)


def place(a, b, c, bond, ang, tor):
    """NeRF: new atom d with |cd| = bond, angle(b,c,d) = ang, dihedral(a,b,c,d) = tor (rad)"""
    bc = unit(c - b)
    n = unit(np.cross(b - a, bc))
    m = np.cross(n, bc)
    return c + bond * (-np.cos(ang) * bc + np.sin(ang) * np.cos(tor) * m
                       + np.sin(ang) * np.sin(tor) * n)


# =============================================================================
#  syndiotactic PP (planar zigzag in the xz-plane, methyls alternating +-y)
# =============================================================================
def build_pp(n):
    if n % 2:
        sys.exit("PP: n must be even (syndiotactic repeat = 2 monomers)")
    bCC, bCM, bCH = 1.54, 1.53, 1.09
    theta = np.radians(112.0)
    d, h = bCC * np.sin(theta / 2), bCC * np.cos(theta / 2)
    half = np.radians(109.47 / 2)
    tilt = np.radians(180.0 - 109.47)
    atoms = []
    for j in range(2 * n):                               # backbone: CH2, CH, CH2, ...
        up = 1.0 if j % 2 == 0 else -1.0
        C = np.array([j * d, 0.0, up * h / 2])
        atoms.append(("C", C))
        sub = [np.array([0.0, s * np.sin(half), up * np.cos(half)]) for s in (1.0, -1.0)]
        if j % 2 == 0:
            atoms += [("H", C + bCH * s) for s in sub]
            continue
        side = ((j - 1) // 2) % 2                        # methyl side alternates
        u = sub[side]
        atoms.append(("H", C + bCH * sub[1 - side]))
        Cm = C + bCM * u
        atoms.append(("C", Cm))
        e1 = np.array([1.0, 0.0, 0.0])
        e2 = np.cross(u, e1)
        v = np.array([d, 0.0, -up * h])
        v -= v.dot(u) * u
        phi0 = np.arctan2(v.dot(e2), v.dot(e1)) + np.pi / 3
        for k in range(3):
            ph = phi0 + 2 * np.pi * k / 3
            dirn = np.cos(tilt) * u + np.sin(tilt) * (np.cos(ph) * e1 + np.sin(ph) * e2)
            atoms.append(("H", Cm + bCH * dirn))
    return atoms, 2 * n * d, ["C", "H"]


# =============================================================================
#  bisphenol-A polycarbonate
#  backbone "virtual bonds": O_b -(ring A)- Cq -(ring B)- O_a - C(=O) - O_b(next)
#  O_b, C1, C4, Cq are collinear (para-phenylene), so each ring is a straight rod.
# =============================================================================
R_ARO, R_OC, R_CO, R_AR, R_ARH, R_ARCQ, R_CQME, R_CH = 1.40, 1.34, 1.20, 1.39, 1.08, 1.53, 1.54, 1.09
A_CQ, A_O, A_C, A_ME = 110.0, 118.0, 107.0, 108.0      # Car-Cq-Car, Car-O-C, O-C-O, Me-Cq-Me
L_ROD = R_ARO + 2 * R_AR + R_ARCQ


def pc_backbone(phiA, phiB, nmon):
    """vertices [C(prev), O_b, Cq, O_a, C, O_b, ...]; carbonate trans,trans (180/180)"""
    pts = [R_OC * np.array([np.cos(A_O * deg), np.sin(A_O * deg), 0.0]),
           np.zeros(3), np.array([L_ROD, 0.0, 0.0])]
    cyc = [(L_ROD, A_CQ, phiA), (R_OC, A_O, phiB), (R_OC, A_C, 180.0), (L_ROD, A_O, 180.0)]
    k = 0
    while len(pts) < 4 * nmon + 3:
        b_, a_, t_ = cyc[k % 4]
        pts.append(place(pts[-3], pts[-2], pts[-1], b_, a_ * deg, t_ * deg))
        k += 1
    return np.array(pts)


def pc_ring(start, ax, ref, psi, first_bond):
    C1 = start + first_bond * ax
    cen = C1 + R_AR * ax
    e1 = perp(ref, ax)
    e2 = np.cross(ax, e1)
    nv = np.cos(psi * deg) * e1 + np.sin(psi * deg) * e2
    Cs = [cen + R_AR * (np.cos(b * deg) * ax + np.sin(b * deg) * nv)
          for b in (180, 120, 60, 0, -60, -120)]
    Hs = [Cs[k] + R_ARH * unit(Cs[k] - cen) for k in (1, 2, 4, 5)]
    return Cs, Hs


def pc_methyl(Cq, w, ref):
    C = Cq + R_CQME * w
    e1 = perp(ref, w)
    e2 = np.cross(w, e1)
    tilt = (180.0 - 109.47) * deg
    return C, [C + R_CH * (np.cos(tilt) * w + np.sin(tilt) * (np.cos(p) * e1 + np.sin(p) * e2))
               for p in (np.pi, np.pi + 2 * np.pi / 3, np.pi + 4 * np.pi / 3)]


def pc_atoms(P, nmon, psiA, psiB):
    atoms = []
    for i in range(nmon):
        Ccp, Ob, Cq, Oa, Cc, Obn = P[4 * i:4 * i + 6]
        a, b = unit(Cq - Ob), unit(Oa - Cq)
        CA, HA = pc_ring(Ob, a, Ccp - Ob, psiA, R_ARO)          # ring A: O_b -> Cq
        CB, HB = pc_ring(Cq, b, Ob - Cq, psiB, R_ARCQ)          # ring B: Cq -> O_a
        atoms.append(("O", Ob))
        atoms += [("C", c) for c in CA] + [("H", h) for h in HA] + [("C", Cq)]
        bis, nrm, g = unit(b - a), unit(np.cross(-a, b)), A_ME / 2 * deg
        for s in (1.0, -1.0):                                   # isopropylidene methyls
            CM, HM = pc_methyl(Cq, -np.cos(g) * bis + s * np.sin(g) * nrm, CA[3] - Cq)
            atoms += [("C", CM)] + [("H", hh) for hh in HM]
        atoms += [("C", c) for c in CB] + [("H", h) for h in HB]
        atoms += [("O", Oa), ("C", Cc),
                  ("O", Cc + R_CO * unit(-(unit(Oa - Cc) + unit(Obn - Cc))))]  # C=O
    return atoms


def pc_screw(phiA, phiB):
    """rotation R, translation t of the operation mapping repeat unit i onto i+1"""
    P = pc_backbone(phiA, phiB, 2)

    def frame(p_prev, p0, p1):
        e1 = unit(p1 - p0)
        e2 = perp(p_prev - p0, e1)
        return np.column_stack([e1, e2, np.cross(e1, e2)])

    R = frame(P[4], P[5], P[6]) @ frame(P[0], P[1], P[2]).T
    return R, P[5] - R @ P[1]


def solve_phiB(phiA, guess):
    """phiB that makes the helix exactly 2_1 (180 deg per repeat unit) for a given phiA"""
    f = lambda b: 1.0 + (np.trace(pc_screw(phiA, b)[0]) - 1.0) / 2.0     # 1 + cos(theta)
    lo, hi, g = guess - 5.0, guess + 5.0, (np.sqrt(5.0) - 1.0) / 2.0
    for _ in range(80):
        c1, c2 = hi - g * (hi - lo), lo + g * (hi - lo)
        lo, hi = (lo, c2) if f(c1) < f(c2) else (c1, hi)
    return 0.5 * (lo + hi)


def build_pc(n, phiA=140.0, phiB_guess=20.6, psiA=130.0, psiB=60.0):
    """defaults: lowest-energy 2_1 conformer found with ffield.reax.cho (after QEq minimisation)"""
    if n % 2:
        sys.exit("PC: n must be even (2_1 helix = 2 repeat units per period)")
    phiB = solve_phiB(phiA, phiB_guess)
    P = pc_backbone(phiA, phiB, n + 1)
    R, t = pc_screw(phiA, phiB)
    theta = np.degrees(np.arccos(np.clip((np.trace(R) - 1) / 2, -1, 1)))
    if abs(theta - 180.0) > 1e-3:
        sys.exit(f"PC: backbone dihedrals give a {theta:.4f} deg helix, need 180 (2_1)")
    w_, V = np.linalg.eig(R)
    u = unit(np.real(V[:, np.argmin(abs(w_ - 1))]))
    if t.dot(u) < 0:
        u = -u
    rise = t.dot(u)
    c = 0.5 * (t - rise * u)                   # a point on the screw axis

    atoms = pc_atoms(P, n, psiA, psiB)
    X = np.array([p for _, p in atoms]) - c
    # rotate: helix axis -> x, first carbonyl C=O (perp. to axis) -> z
    Cc0, Od0 = X[31], X[32]
    wz = perp(Od0 - Cc0, u)
    vy = np.cross(wz, u)
    X = X @ np.vstack([u, vy, wz]).T
    return [(e, X[k]) for k, (e, _) in enumerate(atoms)], n * rise, ["C", "H", "O"]


# =============================================================================
def check(atoms, Lx):
    """valence of every atom (minimum image along x) and closest contact beyond 1-3 neighbours"""
    X = np.array([p for _, p in atoms])
    el = [e for e, _ in atoms]
    D = X[:, None, :] - X[None, :, :]
    D[..., 0] -= Lx * np.round(D[..., 0] / Lx)
    r = np.sqrt((D ** 2).sum(-1))
    np.fill_diagonal(r, 99.0)
    A = r < 1.75
    want = {"H": {1}, "O": {1, 2}, "C": {3, 4}}
    bad = [i for i in range(len(el)) if A[i].sum() not in want[el[i]]]
    near = A | ((A.astype(int) @ A.astype(int)) > 0)          # 1-2 and 1-3 pairs
    return bad, np.where(near, 99.0, r).min()


def write_data(fname, atoms, Lx, elements, title):
    types = {e: k + 1 for k, e in enumerate(elements)}
    X = np.array([p for _, p in atoms])
    ext = np.abs(X[:, 1:]).max() + 10.0
    with open(fname, "w") as f:
        f.write(f"{title}\n\n{len(atoms)} atoms\n{len(elements)} atom types\n\n")
        f.write(f"0.0 {Lx:.6f} xlo xhi\n{-ext:.3f} {ext:.3f} ylo yhi\n{-ext:.3f} {ext:.3f} zlo zhi\n\n")
        f.write("Masses\n\n")
        for e in elements:
            f.write(f"{types[e]} {MASS[e]}  # {e}\n")
        f.write("\nAtoms # charge\n\n")
        for i, (e, p) in enumerate(atoms, 1):
            f.write(f"{i} {types[e]} 0.0 {p[0] % Lx:.6f} {p[1]:.6f} {p[2]:.6f}\n")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("polymer", choices=["PP", "PC"])
    ap.add_argument("-n", type=int, default=None, help="monomers / repeat units (even)")
    ap.add_argument("-o", "--outdir", default=WORKDIR, help=f"output directory (default {WORKDIR})")
    args = ap.parse_args()

    if args.polymer == "PP":
        n = args.n or 20
        atoms, Lx, elements = build_pp(n)
        title = f"syndiotactic PP, periodic chain along x, {n} monomers"
    else:
        n = args.n or 6
        atoms, Lx, elements = build_pc(n)
        title = f"bisphenol-A polycarbonate (C16H14O3)n, periodic 2_1 chain along x, {n} repeat units"

    if n < 2:
        sys.exit("n must be >= 2")
    bad, rmin = check(atoms, Lx)
    if bad:
        sys.exit(f"geometry check failed for atoms {bad[:10]}")
    if Lx < 25.0:
        print(f"WARNING: Lx = {Lx:.1f} A is short compared with the 10 A ReaxFF/QEq cutoff; use a larger n")
    try:
        os.makedirs(args.outdir, exist_ok=True)
    except OSError as err:
        sys.exit(f"cannot create {args.outdir} ({err}); pass another folder with -o")
    fname = os.path.join(args.outdir, f"{args.polymer}_chain.data")
    write_data(fname, atoms, Lx, elements, title)
    formula = " ".join(f"{e}{sum(1 for a, _ in atoms if a == e)}" for e in elements)
    print(f"{fname}: {len(atoms)} atoms ({formula}), Lx = {Lx:.3f} A, "
          f"closest contact beyond 1-3 neighbours = {rmin:.2f} A")
