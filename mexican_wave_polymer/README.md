# Mexican wave: travelling electric field on a polymer chain (LAMMPS + ReaxFF)

<img width="720" height="344" alt="PP_PC_field_wave" src="https://github.com/user-attachments/assets/0023ab4f-a721-4607-8dad-8cc074b18122" />


Applies a sinusoidal, travelling, transverse electric field

    Ey(x,t) = E0 · sin(k·x − ω·t),   k = 2π·nwave/Lx,   ω = 2π/Tper

to a single infinite (periodic along x) polymer chain, so the wave runs along the chain like a stadium wave. Polypropylene (PP) and bisphenol-A polycarbonate (PC) chains are included; charges are equilibrated with QEq and respond to the field.

## Requirements

- LAMMPS ≥ 15Jun2023 with the REAXFF package
- Python 3 with numpy
- `ffield.reax.cho` (C/H/O ReaxFF, Chenoweth et al. 2008), found in the `potentials/` folder of LAMMPS

## Usage

First, make polymer chains using:

```bash
python make_chain.py PC -n 12 -o .
python make_chain.py PP -n 50 -o .
```

`-n` is the number of repeat units (must be even). This writes `PC_chain.data` / `PP_chain.data`.

Then, copy `ffield.reax.cho` into the same folder and run the LAMMPS script, you may want use:

```bash
lmp -in efield_wave.in -log none -var workdir $(pwd) -var sys PP
lmp -in efield_wave.in -log none -var workdir $(pwd) -var sys PC
```

`workdir` must be the folder containing the data files and the force field.

## Main parameters

Change any of them on the command line with `-var name value`.

| Variable | Default | Meaning |
|----------|---------|---------|
| `E0` | 0.1 | field amplitude (V/Å) |
| `Tper` | 1000 | wave period (fs) |
| `nwave` | 1 | wavelengths in the box |
| `ncyc` | 10 | periods with the field on (sets trajectory length) |
| `neq` | 40000 | equilibration steps without field (not in trajectory) |
| `Temp` | 300 | temperature (K) |

## Output

- `dump/` holds the trajectory of the field stage, for OVITO/VMD. Colour atoms by `q` to see the charge wave.
- `data/` holds the log, slab profiles along x, species (bond-breaking check), and the minimised and final structures.

File names carry a tag with the system and parameters, e.g. `PP_E0.1_n1_P1000.0_T300.0`, so runs with different settings don't overwrite each other.
