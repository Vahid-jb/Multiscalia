/*
 * (COMSOL 6.x with Structural Mechanics Module): memory + hinge settlement
 *
 * Version 2026-10-22 (values checked against sources; see Window_DigitalTwin_changes_2026-10-22.txt)
 *   - concrete C25/30: E = 31 GPa (EN 1992-1-1 Table 3.1), weight 25 kN/m^3 (EN 1991-1-1 Annex A),
 *     conductivity 2.1 W/(m K); one parameter each (E_con, rho_con, k_con), used by material and creep branch
 *   - masonry: E_mas = 1000*f_k_mas (EN 1996-1-1 3.7.2), used by material and creep branch
 *   - aluminium frame/sash: nu = 0.3, alpha = 23e-6 1/K (EN 1999-1-1)
 *   - setting blocks about 100 mm from the glass corner (Baunetz Wissen / TR 3), was 50 mm
 *   - G_lk_z moved below E0_lk; heater description corrected to 1500 W; creep-fit accuracy stated year by year
 *
 * Version 2026-10-25
 *   - window installed as an inward-opening turn-tilt window: the frame has an outer stop lip, the sash
 *     (unchanged size and properties) stands 14 mm proud on the room side and closes outward against it
 *     with a 4 mm EPDM stop gasket; before, nothing stopped the sash from moving outward
 *   - decorative handle on the room side of the lock-side sash member (light, soft, no effect on results)
 *
 * Version 2026-10-25 b
 *   - dead load of the storeys above applied on the wall tops (down through the walls), not on the slab span;
 *     the live load of the floor above stays on the slab span
 *   - sash and frame meet only at hinges, locks and the stop gasket; the stop gasket has a real gasket stiffness
 *     (EN 12365-1) and a closing pressure; the centre gasket only takes part in the heat transfer
 *   - hinge wear follows the number of openings (4 per day, Archard), setting-block set stays time-based
 *   - new outputs gk_open, gk_press, seal_margin (seal opening between sash and frame)
 *
 * Version 2026-10-27
 *   - walls of the storey above added up to their mid-height and held there across their own plane, so the ceiling
 *     slab end is held as in a real multi-storey building; before, the slab end and the facade wall head could rotate
 *     freely, so a floor load tilted the facade wall and the window outward. Switch: ADD_UPPER_WALL
 *   - the storeys' dead load now acts on top of those walls (p_roof_top), the same total as before
 *
 * Version 2026-11-05
 *   - tilt and full (turn) opening. Usage pattern on the season clock: n_turn full openings a day all year,
 *     t_turn_w (midwinter) to t_turn_s (midsummer) each; tilting only in the warm months, n_tilt_s a day at
 *     midsummer, h_tilt each. Turn and tilt cycles counted separately (N_turn, N_tilt), days open, tilted and
 *     closed reported (D_turn, D_tilt, D_closed)
 *   - hinge wear per part and cycle type (Archard: force x sliding). Turning wears the corner bearing (its pin
 *     turns 90 deg under the cantilevered sash), tilting wears the scissor arm (it slides in its guide). Cross
 *     weights w_cb_tilt, w_ss_turn from the sash weight and the hardware geometry
 *   - tilt bearing (Kipplager) at the bottom lock-side corner, engaged when tilted; optional sash lifter there when
 *     closed (f_lifter, default 0: as before)
 *   - Studies 3 and 4: sash tilted and turned open (locks and stop gasket disengaged, gravity on the sash turned by
 *     the tilt angle), new window and after t_chk = 10 years of wear; outputs: hinge forces F_cb_v, F_cb_h, F_ss_h,
 *     F_ss_z, F_tb_v and the lock-corner drop while open
 *
 * Version 2026-11-05b: realistic use inside the aging run
 *   - the window is closed most of the time, sometimes tilted, sometimes open (adjustable). The states switch daily,
 *     the soft parts relax over months and years, so each part ages under its time-averaged load (linear
 *     viscoelasticity, Boltzmann superposition). Each soft part gets r_eff = 1 - phi*(1 - r), phi = sum over the states
 *     of (time share) x (load of the part in that state / its load when closed). Wear counts the movements.
 *   - Study 5: the same aging with the window never opened (f_use = 0), so Study 1 - Study 5 = effect of use
 *   - Studies 3 and 4 reduced to load-path checks of the new window (they confirm the load ratios behind phi)
 */

import com.comsol.model.*;
import com.comsol.model.util.*;

public class Window_DigitalTwin {

  static final boolean ADD_SHUTTER_BOX = false;   // true: roller-shutter box above the window
  static final boolean ADD_UPPER_WALL = true;     // true: walls of the storey above, to their mid-height, hold the ceiling slab end

  static final String TITLE = "Window digital twin: frame, sash, material memory and hinge settlement";
  static final String DESCRIPTION =
    "PURPOSE\n" +
    "This model follows how the openable sash of a window and its fixed frame drift out of alignment over the years. It combines heat transfer, the weights acting on room and window, and the aging of the soft parts that connect wall, frame, sash and glass. In this version the soft parts have MEMORY: what happened earlier (how long and how hard a gasket was squeezed, how warm it was) affects their state today.\n" +
    "\n" +
    "GEOMETRY (all sizes are the strain-free sizes at T_ref = 20 deg C)\n" +
    "- Room W_in x H_in x L_in inside (default 4 x 3 x 4 m), 30 cm insulated masonry walls (U_wall = 0.8), 20 cm concrete floor and ceiling slabs, concrete lintel over the opening.\n" +
    "- Window in the middle of the wall depth: fixed frame 1.23 x 1.48 m (50 mm face, 75 mm deep) in a 12 mm PU-foam joint; openable sash (same 75 mm depth) with a 5 mm rebate gap, standing 14 mm proud on the room side and closing outward against the frame stop lip (12 mm overlap, 4 mm EPDM stop gasket), as in an inward-opening turn-tilt window, so it can move into the room but not out of the frame; the outer gap chamber between centre gasket and stop is taken as drained to the outside (its faces see outdoor air); EPDM centre gasket; hinges on the left (corner bearing = 2 blocks, scissor stay = 1 block) and 4 locking points in the room-side 20 mm of the gap; double glazing 4-14-4 (22 mm) between 4 mm EPDM bedding on both glass faces; the 5 mm edge clearance around the glass is air (a void in the model), bridged only by the 4 setting blocks (2 load-bearing placed diagonally, 2 distance blocks); a decorative handle on the room side of the lock-side sash member marks the inside.\n" +
    "- Heater 3.0 x 0.40 x 0.106 m, 4 cm in front of the wall; heat transfer only.\n" +
    "- Frame, sash and glazing are homogenised solids: conductivity from Uf and Ug, stiffness from the profile moment of inertia (I_fr, I_sa) and from the two 4 mm panes.\n" +
    "\n" +
    "HEAT TRANSFER (time-dependent in Study 1, so wall and window heat capacity count)\n" +
    "- Conduction in all solids; wall, frame and glazing conductivity k = d/(1/U - Rsi - Rse), so each element reaches its U-value.\n" +
    "- Room-side surfaces: room air T_in = 20 deg C held by a thermostat, h_in = 7.69 W/(m^2*K) (Rsi = 0.13, still air).\n" +
    "- Outside surfaces: h_out = 25 W/(m^2*K) (Rse = 0.04, wind-swept) to T_out_v: monthly mean curve T_mean - A_seas*cos(2*pi*(t/1[a] + t_inst)) in Study 1, -5 deg C in Study 2.\n" +
    "- Neighbour rooms 18 deg C (T_adj) on side and back walls, the room above (slab top and the inner faces of the walls above) and floor bottom; outdoor air also on the facade of the walls above; e_left ... e_down = 1 switches a side to outdoor air.\n" +
    "- Heater: P_dem_v = P_heat*(T_in - T_out_v)/(T_in - T_design), never below 0, P_heat = q_vol*room volume; 4 cm air gap to the wall as an effective solid.\n" +
    "- Sun: absorbed heat = absorptance * G_t on facade, frame/sash and glazing; Study 2: off; Study 1: G_mean, a constant daily mean (127 W/m^2 is a horizontal all-hours mean, an upper value for a facade). Sunlight is heat only; UV aging is not modelled.\n" +
    "\n" +
    "SOLID MECHANICS (quasi-static: slow loads, no inertia)\n" +
    "- All solids except heater and air gap; floor slab on rollers. The walls of the storey above are modelled up to their mid-height (h_up), where a continuous wall bends through zero; there each is held across its own plane and is free along it and upward. This holds the ceiling slab end as in a real multi-storey building (without it, the slab end and the facade wall head could rotate freely). ADD_UPPER_WALL = false restores the old free top with rigid-motion suppression.\n" +
    "- Loads, all multiplied by the start-up ramp f_ramp (0 to 1 during t_ramp = 1 day): window self-weight; building self-weight and the 12 kPa dead load of the storeys above (f_dead), which comes down through the walls: it acts on top of the walls above (p_roof_top; their lower half is modelled and weighs the rest), not on the slab span; 3 kPa live load of the floor above on the ceiling slab span (f_live), of which only the quasi-permanent share psi2_live = 0.3 acts in the aging study (EN 1990, Table A1.1), because only that part is present long enough to creep; wind 0.5 kPa (f_wind, 0 = off).\n" +
    "- Thermal expansion (Multiphysics > Thermal Expansion): every part changes size by alpha*(T - T_ref), T_ref = 20 deg C, at its own temperature.\n" +
    "- Locks (Linear Elastic Material 3, orthotropic): stiff out of plane (E0_lk), so they press the sash onto the gasket; soft in plane (E_lk_ip, G_lk_ip), because the cams slide in their keepers. The sash weight is therefore carried by the hinges, as in a real window.\n" +
    "- Sash and frame meet only at the hinges, the locks and the stop gasket, so they can deform independently. The stop gasket has the stiffness of a real window gasket (EN 12365-1: F_gk = 50 N/m over a working range c_gk = 3 mm), so it presses but carries almost no shear; with the window closed it is compressed by c0_gk = 2 mm (closing pressure, f_gkp). The gasket pushes the sash into the room and the frame stop outward, so between the locks they bow apart. The centre gasket takes part in the heat transfer only.\n" +
    "\n" +
    "AGING\n" +
    "- Soft parts (gasket, bedding, PU foam, hinges, locks, setting blocks): Linear Elastic Material 2 holds the long-term stiffness E_inf = r*E0; its Viscoelasticity sub-node adds one relaxing branch (shear modulus Gv_loc, relaxation time tau_loc = tau*1 a), so the stiffness right after loading equals E0 and relaxes towards r*E0. Each soft group has its own E0, r, tau and Poisson ratio in the domain variable groups 'Soft part: ...'. Because COMSOL integrates the branch strains in time, the state is carried forward: earlier loads keep acting, stresses relax, and parts partly recover when the load drops.\n" +
    "- Masonry and concrete do not lose stiffness: their modulus stays E0 (E_mas = 3 GPa, E_con = 31 GPa) for every load. They creep: under a lasting stress the strain grows by dphi*(elastic strain). Only the creep still to come after the window is installed matters, because the structure has carried its own weight and the storeys above for t_shell years before. EN 1992-1-1 Annex B: phi(t) = phi0*beta_c(t), beta_c = ((t - t0)/(beta_H + t - t0))^0.3, with phi0_con = 2.65 (C25/30, RH 50 %, h0 = 200 mm, loaded at 28 days) and beta_H = 550 days; masonry: phi_mas = 1.0 (EN 1996-1-1 Table 3.8, clay units) with the same time shape (beta_H = 700 days, an assumption). For t_shell = 0.5 years the concrete has already reached phi = 1.75 at installation and reaches 2.55 ten years later, so dphi_con_10 = 0.80; masonry dphi_mas_10 = 0.33.\n" +
    "- This creep is one viscoelastic branch in Linear Elastic Material 2 (the same mechanism as the soft parts), with retardation time tauc_cr = 1.5 years and strength dphi = dphi_10/(1 - exp(-10/tauc_cr)), so the 10-year value equals EN exactly; from year 1 to 10 the branch stays within 10 % of EN for concrete (largest +9 % at year 3) and within 12 % for masonry, and in the first months it lags by at most 0.04 in phi. The bulk modulus stays at its instantaneous value (creep is deviatoric) and Poisson's ratio right after loading is 0.2. Short loads (seasonal temperature, wind) therefore meet the full stiffness, lasting loads creep.\n" +
    "- Densities, conductivities, expansion coefficients, geometry and loads never age.\n" +
    "\n" +
    "HINGE SETTLEMENT (the cumulative part)\n" +
    "- Viscoelastic relaxation is bounded and partly recovers, so on its own it gives only a small drift. Real sashes drop mainly through wear and play in the hinges and permanent set of the setting blocks. The model prescribes this as built-in strains (Linear Elastic Material 2 > Initial Stress and Strain), zero at installation. Hinge wear follows the openings, counted separately for turn and tilt (see Opening and tilting); wear grows in proportion to force x sliding (Archard), with N_ref = 20 000 cycles (EN 12400 class 3) as reference. The setting blocks creep: s_set = f_set*(1 - exp(-(t/1[a] - t0_ref)/tau_set)).\n" +
    "- Corner bearing (bottom block): sinks by d_cb_set = dv_cb_inf*s_cb. Scissor stay (top block): play lets the sash top move away from the jamb by d_ss_set = dh_ss_inf*s_ss. Load-bearing setting blocks: permanent set d_sb_set = d_sb_inf*s_set each.\n" +
    "- dv_cb_inf, dh_ss_inf, d_sb_inf and tau_set are ASSUMPTIONS: calibrate them with measured sash drop or with the adjustment intervals of the fitter. f_set = 0 switches the settlement off.\n" +
    "- The model then computes the consequences: how the lock corner drops (sag_lock), which gaps close (g_*), when the sash starts to rub (rub_margin < 0).\n" +
    "\nRealistic use: closed, tilted, open\n" +
    "- The window is closed most of the time, sometimes tilted and sometimes fully open. Usage on the season clock (adjustable): n_turn = 3 full openings a day all year (airing by opening wide, as the Umweltbundesamt advises 2 to 4 times a day), 5 min each at midwinter rising to 20 min at midsummer; tilting only in the warm months (long tilting is advised against in the cold season): n_tilt_s = 1 a day at midsummer, none at midwinter, h_tilt = 6 h each (assumption). Year averages: fully open f_turn_t, tilted f_tilt_t, closed f_closed_t of the time. f_use = 0: never opened.\n" +
    "- How use enters the aging. The states switch every day, the soft parts relax over months and years; for linear viscoelastic parts (Boltzmann superposition) the branch strain then follows the time-averaged load. A part that carries its closed-state load only part of the time relaxes less, because it rests while unloaded. Each soft part therefore gets an effective long-term ratio r_eff = 1 - phi*(1 - r), phi = sum over the states of (time share) x (load of the part in that state / its load when closed): stop gasket and locks phi = f_closed_t (loaded only when closed); corner bearing phi = f_closed_t + f_turn_t + f_tilt_t*cos(th_tilt)/2 (full weight closed and turned, half when tilted); hinge side block and scissor phi = f_closed_t + f_turn_t (the cantilever couple vanishes when tilted); tilt bearing phi = f_tilt_t. Bedding, setting blocks, PU foam, masonry and concrete carry the same load in every state and keep their r. The fit is always measured with the window closed.\n" +
    "- Why not three separate ten-year runs (always closed, always tilted, always open) averaged afterwards: the fit is defined only with the window closed; in an always-open run the gasket and locks never carry load and never relax, so averaged results would mix three different structures; and wear depends on the number of movements, not on the time spent in a state.\n" +
    "- Wear counts the movements (Archard: force x sliding). A turn wears the corner bearing: its pin turns 90 deg under the cantilevered sash. A tilt wears the scissor arm: it slides about a_tilt in its guide. Cross weights from statics: w_cb_tilt = F_tilt*th_tilt/(F_turn*pi/2), w_ss_turn = F_turn*r_pin*(pi/2)/(F_tilt*s_arm). s_cb = f_set*(N_turn + w_cb_tilt*N_tilt)/N_ref, s_ss = f_set*(N_tilt + w_ss_turn*N_turn)/N_ref. N_turn, N_tilt count the movements since installation; D_turn, D_tilt, D_closed the days open, tilted and closed. r_pin, a_tilt, the tilt habits and the reference wear values are assumptions to calibrate.\n" +
    "- Tilt bearing / sash lifter (bottom lock-side corner): carries the sash when tilted; when closed only if f_lifter = 1 (a sash lifter; default 0).\n" +
    "\n" +
    "STUDIES\n" +
    "- Study 1, aging with memory (one click): time-dependent from t = 0 to t_end years, output at 0, 0.002, 0.01, 0.05 years and then every dt_out years. At t = 0 the model is unloaded at 20 deg C; loads ramp up during the first day and the temperatures settle within days. The state at t0_ref = 0.05 years (about 18 days) is the installation reference: from there everything is carried forward in time, nothing is restarted.\n" +
    "- Study 2, design winter day check: one stationary solve at -5 deg C, building weight off, Linear Elastic Material 2 switched off so the soft parts use their new stiffness E0.\n" +
    "\n" +
    "OUTPUTS (Definitions > Frame-sash alignment, Checks)\n" +
    "- g_top_L ... g_lck_T: change of the 5 mm rebate gap at both ends of each side, positive opens; rub_margin = 5 mm + largest closure (below 0: the sash rubs); sag_lock: vertical drop of the lock-side bottom corner relative to the frame; skew_sash, skew_frame; dz_BL ... dz_TL, dz_max: out-of-plane step sash-frame; twist_rel; gk_open: largest opening of the stop gasket, seal_margin = c0_gk - gk_open (below 0: the seal has lost contact, air and sound get through); gk_press: largest extra compression.\n" +
    "- Checks: v_lintel (mean vertical displacement of the lintel underside), T_frame, T_sash, T_glass, P_dem_v.\n" +
    "- Table 'Aging: change since installation' and the 1D plot show value(t) - value(t0_ref) over time; the plot 'Hinge settlement (input) and sash response' compares the prescribed settlement with the computed sash drop.\n" +
    "- 3D plot 'Aging drift since installation, window only (scaled)': displacement minus its value at t0_ref, deformation x20, fixed colour range. Its animation shows only the winters (t = 1, 2, ..., 10 years), so the seasonal swing does not hide the drift.\n" +
    "\n" +
    "HOW TO USE\n" +
    "- Study 3, sash tilted, and Study 4, sash turned open: load-path checks of the new window (stationary, mechanics only at 20 deg C, window weight only, locks and stop gasket disengaged). Their hinge forces F_cb_v, F_cb_h, F_ss_h, F_ss_z, F_tb_v, compared with the closed window (Study 2), confirm the load ratios behind phi.\n" +
    "- Study 5, aging with the window never opened (f_use = 0): the same run as Study 1. The table Aging, window never opened next to the Aging table shows the effect of use.\n" +
    "- Change dt_out and t_end to refine or extend the aging; press Compute on Study 1. Studies 2 to 5 run separately; Study 5 takes as long as Study 1.\n" +
    "- The animation 'aging drift, winters only' uses the stored outputs 4, 8, ..., 44 (t = 0.05 and the winters 1 ... 10) for dt_out = 0.25 and t_end = 10; for other values choose the winter times in the animation by hand. Its colour range (0 to 1.6 mm) and deformation scale (x20) are fixed so frames can be compared; adjust them if you change the settlement values.\n" +
    "- If the build printed a NOTE about the viscoelastic node: open Solid Mechanics > Linear Elastic Material 2 > Viscoelasticity 1, choose Material model Standard linear solid (or Generalized Maxwell with one branch), enter shear modulus Gv_loc and relaxation time tauv_loc, and enable the node.\n" +
    "- After changing geometry parameters (for example L_in), rebuild geometry and mesh and compute again.\n" +
    "\n" +
    "LIMITS\n" +
    "- The viscoelastic values r and tau and the settlement values are assumptions until fitted to tests and measurements. Hinge wear is prescribed, not computed from opening cycles. Sun as a constant daily mean; masonry and concrete creep linearly (no shrinkage, no cracking, creep fitted for t_shell, recompute if the structure is older or younger at installation); hinges are blocks with one stiffness in all directions.";

  static void setText(Model m, String method, String text) {
    try {
      m.getClass().getMethod(method, String.class).invoke(m, text);
    } catch (Exception e) {
      System.out.println("NOTE: paste the model " + method + " into the root node by hand: " + e);
    }
  }

  static String C = "comp1";

  static void blk(Model m, String tag, String label, String[] pos, String[] size) {
    m.component(C).geom("geom1").create(tag, "Block");
    m.component(C).geom("geom1").feature(tag).label(label);
    m.component(C).geom("geom1").feature(tag).set("base", "corner");
    m.component(C).geom("geom1").feature(tag).set("pos", pos);
    m.component(C).geom("geom1").feature(tag).set("size", size);
  }

  static void dif(Model m, String tag, String label, String in, String[] cut) {
    m.component(C).geom("geom1").create(tag, "Difference");
    m.component(C).geom("geom1").feature(tag).label(label);
    m.component(C).geom("geom1").feature(tag).selection("input").set(in);
    m.component(C).geom("geom1").feature(tag).selection("input2").set(cut);
  }

  static void box(Model m, String tag, String label, int dim, String x0, String x1, String y0, String y1,
                  String z0, String z1) {
    m.component(C).selection().create(tag, "Box");
    m.component(C).selection(tag).label(label);
    m.component(C).selection(tag).set("entitydim", dim);
    m.component(C).selection(tag).set("xmin", x0);
    m.component(C).selection(tag).set("xmax", x1);
    m.component(C).selection(tag).set("ymin", y0);
    m.component(C).selection(tag).set("ymax", y1);
    m.component(C).selection(tag).set("zmin", z0);
    m.component(C).selection(tag).set("zmax", z1);
    m.component(C).selection(tag).set("condition", "inside");
  }

  static void sdiff(Model m, String tag, String label, int dim, String[] add, String[] sub) {
    m.component(C).selection().create(tag, "Difference");
    m.component(C).selection(tag).label(label);
    m.component(C).selection(tag).set("entitydim", dim);
    m.component(C).selection(tag).set("add", add);
    m.component(C).selection(tag).set("subtract", sub);
  }

  static void sint(Model m, String tag, String label, int dim, String[] in) {
    m.component(C).selection().create(tag, "Intersection");
    m.component(C).selection(tag).label(label);
    m.component(C).selection(tag).set("entitydim", dim);
    m.component(C).selection(tag).set("input", in);
  }

  static void suni(Model m, String tag, String label, int dim, String[] in) {
    m.component(C).selection().create(tag, "Union");
    m.component(C).selection(tag).label(label);
    m.component(C).selection(tag).set("entitydim", dim);
    m.component(C).selection(tag).set("input", in);
  }

  static void pt(Model m, String tag, String x, String y, String z) {
    m.component(C).selection().create(tag, "Ball");
    m.component(C).selection(tag).label("Point " + tag);
    m.component(C).selection(tag).set("entitydim", 0);
    m.component(C).selection(tag).set("posx", x);
    m.component(C).selection(tag).set("posy", y);
    m.component(C).selection(tag).set("posz", z);
    m.component(C).selection(tag).set("r", "1[mm]");
    m.component(C).selection(tag).set("condition", "intersects");
  }

  static boolean presDisp(Model m, String tag, String label, String sel, int comp) {
    try {
      m.component(C).physics("solid").create(tag, "Displacement2", 2);
      m.component(C).physics("solid").feature(tag).label(label);
      m.component(C).physics("solid").feature(tag).selection().named(sel);
      try {
        m.component(C).physics("solid").feature(tag).setIndex("Direction", "prescribed", comp);
      } catch (Exception e1) {
        m.component(C).physics("solid").feature(tag).setIndex("Direction", "1", comp);
      }
      m.component(C).physics("solid").feature(tag).setIndex("U0", "0", comp);
      return true;
    } catch (Exception e) {
      System.out.println("NOTE: add Solid Mechanics > Prescribed Displacement '" + label + "' on " + sel + " (direction " + "xyz".charAt(comp) + " = 0), then disable Rigid Motion Suppression. " + e.getMessage());
      return false;
    }
  }

  static void mat(Model m, String tag, String label, String sel, String k, String rho, String cp,
                  String alpha, String E, String nu) {
    m.component(C).material().create(tag, "Common");
    m.component(C).material(tag).label(label);
    if (sel == null) m.component(C).material(tag).selection().all();
    else m.component(C).material(tag).selection().named(sel);
    m.component(C).material(tag).propertyGroup("def").set("thermalconductivity", new String[]{k, "0", "0", "0", k, "0", "0", "0", k});
    m.component(C).material(tag).propertyGroup("def").set("density", rho);
    m.component(C).material(tag).propertyGroup("def").set("heatcapacity", cp);
    m.component(C).material(tag).propertyGroup("def").set("thermalexpansioncoefficient", new String[]{alpha, "0", "0", "0", alpha, "0", "0", "0", alpha});
    m.component(C).material(tag).propertyGroup().create("Enu", "Young's modulus and Poisson's ratio");
    m.component(C).material(tag).propertyGroup("Enu").set("youngsmodulus", E);
    m.component(C).material(tag).propertyGroup("Enu").set("poissonsratio", nu);
  }

  static void hflux(Model m, String tag, String label, String sel, String h, String Text) {
    m.component(C).physics("ht").create(tag, "HeatFluxBoundary", 2);
    m.component(C).physics("ht").feature(tag).label(label);
    m.component(C).physics("ht").feature(tag).selection().named(sel);
    m.component(C).physics("ht").feature(tag).set("HeatFluxType", "ConvectiveHeatFlux");
    m.component(C).physics("ht").feature(tag).set("h", h);
    m.component(C).physics("ht").feature(tag).set("Text", Text);
  }

  static void qflux(Model m, String tag, String label, String sel, String q) {
    m.component(C).physics("ht").create(tag, "HeatFluxBoundary", 2);
    m.component(C).physics("ht").feature(tag).label(label);
    m.component(C).physics("ht").feature(tag).selection().named(sel);
    m.component(C).physics("ht").feature(tag).set("HeatFluxType", "GeneralInwardHeatFlux");
    m.component(C).physics("ht").feature(tag).set("q0", q);
  }

  static void bload(Model m, String tag, String label, String sel, String[] f) {
    m.component(C).physics("solid").create(tag, "BoundaryLoad", 2);
    m.component(C).physics("solid").feature(tag).label(label);
    m.component(C).physics("solid").feature(tag).selection().named(sel);
    try {
      m.component(C).physics("solid").feature(tag).set("FperArea", f);          // COMSOL 6.0
    } catch (Exception e) {
      try {
        m.component(C).physics("solid").feature(tag).set("forceReferenceArea", f);  // newer versions
      } catch (Exception e2) {
        System.out.println("NOTE: enter the load of '" + label + "' by hand: (" + f[0] + ", " + f[1] + ", " + f[2] + ")");
      }
    }
  }

  public static Model run() {
    Model model = ModelUtil.create("Model");
    model.label("Window_DigitalTwin.mph");
    setText(model, "title", TITLE);
    setText(model, "description", DESCRIPTION);

    // ===================================================================== parameters
    String[][] P = {
      // building
      {"W_in", "4[m]", "Room width inside (x)"},
      {"H_in", "3[m]", "Room height inside (y)"},
      {"L_in", "4[m]", "Room depth inside (z)"},
      {"t_w", "0.30[m]", "Wall thickness"},
      {"t_slab", "0.20[m]", "Floor and ceiling slab thickness"},
      {"h_lin", "0.25[m]", "Lintel height"},
      {"l_bear", "0.25[m]", "Lintel bearing length each side"},
      {"h_sbox", "0.30[m]", "Roller-shutter box height (if used)"},
      {"d_sbox", "0.18[m]", "Roller-shutter box depth from the room side (if used)"},
      // window
      {"W_f", "1.23[m]", "Frame outer width"},
      {"H_f", "1.48[m]", "Frame outer height"},
      {"d_f", "0.075[m]", "Frame and sash depth"},
      {"b_fr", "0.05[m]", "Frame face width"},
      {"s_gap", "0.005[m]", "Rebate gap sash-frame (Falzluft)"},
      {"b_sa", "0.065[m]", "Sash face width"},
      {"y_sill", "0.85[m]", "Frame bottom above floor"},
      {"z_fc", "-t_w/2", "Window mid-plane (wall: -t_w..0)"},
      {"joint", "0.012[m]", "Installation joint width"},
      {"d_gk", "0.02[m]", "Centre gasket depth"},
      {"d_hw", "0.02[m]", "Hardware zone depth (room side of the rebate)"},
      {"L_hw", "0.06[m]", "Hinge block length"},
      {"L_lk", "0.03[m]", "Locking point length"},
      {"t_g", "0.004[m]", "Glass pane thickness"},
      {"t_cav", "0.014[m]", "Gas cavity (4-14-4)"},
      {"t_igu", "2*t_g+t_cav", "Glazing unit thickness"},
      {"e_g", "0.015[m]", "Glass edge cover"},
      {"g_e", "0.005[m]", "Glass edge clearance"},
      {"t_bed", "0.004[m]", "EPDM bedding thickness"},
      {"L_sb", "0.10[m]", "Setting block length: 80 to 100 mm (Baunetz Wissen, Klotzung)"},
      {"a_sb", "0.10[m]", "Setting block distance from the glass corner: about 100 mm for usual window sizes (Baunetz Wissen, Klotzung; TR 3 / EN 12488)"},
      // heater
      {"L_h", "3[m]", "Heater length"},
      {"H_h", "0.40[m]", "Heater height"},
      {"D_h", "0.106[m]", "Heater depth"},
      {"y_h", "0.15[m]", "Heater bottom above floor"},
      {"g_h", "0.04[m]", "Gap heater to wall"},
      // derived positions
      {"xf0", "W_in/2-W_f/2", "Frame x start"},
      {"yf0", "y_sill", "Frame y start"},
      {"zf0", "z_fc-d_f/2", "Frame z start (room side)"},
      {"o_st", "0.012[m]", "Frame stop: overlap of the outer stop lip over the sash (ASSUMPTION, typical 10 to 15 mm)"},
      {"t_lip", "0.010[m]", "Frame stop lip thickness, homogenised profile (ASSUMPTION)"},
      {"t_sg", "0.004[m]", "Stop gasket thickness, compressed (ASSUMPTION)"},
      {"dz_sa", "t_lip+t_sg", "The sash stands this far proud of the frame on the room side and closes outward against the stop"},
      {"zsr0", "zf0-dz_sa", "Sash z start (room-side face of the sash)"},
      {"z_sc", "zsr0+d_f/2", "Sash and glazing mid-plane"},
      {"z_lip0", "zf0+d_f-t_lip", "Room-side face of the frame stop lip"},
      {"xo0", "xf0-joint", "Opening x start"},
      {"yo0", "yf0-joint", "Opening y start"},
      {"W_o", "W_f+2*joint", "Opening width"},
      {"H_o", "H_f+2*joint", "Opening height"},
      {"xfi0", "xf0+b_fr", "Frame inner edge x"},
      {"yfi0", "yf0+b_fr", "Frame inner edge y"},
      {"W_fi", "W_f-2*b_fr", "Frame inner width"},
      {"H_fi", "H_f-2*b_fr", "Frame inner height"},
      {"xsa0", "xfi0+s_gap", "Sash x start"},
      {"ysa0", "yfi0+s_gap", "Sash y start"},
      {"W_sa", "W_fi-2*s_gap", "Sash width"},
      {"H_sa", "H_fi-2*s_gap", "Sash height"},
      {"xc0", "xsa0+b_sa", "Clear opening x start"},
      {"yc0", "ysa0+b_sa", "Clear opening y start"},
      {"W_c", "W_sa-2*b_sa", "Clear opening width"},
      {"H_c", "H_sa-2*b_sa", "Clear opening height"},
      {"xg0", "xc0-e_g", "Glass x start"},
      {"yg0", "yc0-e_g", "Glass y start"},
      {"W_g", "W_c+2*e_g", "Glass width"},
      {"H_g", "H_c+2*e_g", "Glass height"},
      {"zg0", "z_sc-t_igu/2", "Glass z start (glazing centred in the sash)"},
      {"xs0", "xg0-g_e", "Sash rebate x start"},
      {"ys0", "yg0-g_e", "Sash rebate y start"},
      {"W_s", "W_g+2*g_e", "Sash rebate width"},
      {"H_s", "H_g+2*g_e", "Sash rebate height"},
      {"t_s", "t_igu+2*t_bed", "Sash rebate thickness"},
      {"zs0", "z_sc-t_s/2", "Sash rebate z start"},
      {"x_hd", "xsa0+W_sa-b_sa/2", "Handle: centre on the lock-side sash member (decorative)"},
      {"y_hd", "ysa0+H_sa/2", "Handle: rosette centre at mid-height of the sash (decorative)"},
      {"tol", "1[mm]", "Selection tolerance"},

      // climate and heating
      {"T_in", "20[degC]", "Indoor air"},
      {"T_ref", "20[degC]", "Strain-free temperature (installation)"},
      {"T_design", "-5[degC]", "Design outdoor temperature"},
      {"f_season", "1", "1: seasonal climate (aging study, default); 0: design day (set by Study 2)"},
      {"dt_out", "0.25", "Output interval of the aging study (years)"},
      {"t_end", "10", "Duration of the aging study (years)"},
      {"t0_ref", "0.05", "Installation reference (years): loads applied, temperatures settled"},
      {"t_ramp", "1[d]", "Time over which all loads are applied at the start"},
      {"f_set", "1", "Hinge settlement: 1 on, 0 off"},
      // window states (Studies 3 and 4 switch them) and how the window is used
      {"f_lock", "1", "Locks engaged: 1 closed and locked; 1e-4 in Studies 3 and 4 (unlocked, sash open)"},
      {"f_stopg", "1", "Stop gasket in contact: 1 closed; 1e-3 in Studies 3 and 4 (sash away from the frame stop)"},
      {"f_lifter", "0", "Sash lifter supports the bottom lock-side corner when closed: 0 none (default), 1 fitted"},
      {"f_tb", "max(f_lifter,1e-4)", "Bottom lock-side bearing engaged: the sash lifter when closed, the tilt bearing (1) in Study 3"},
      {"st_tilt", "0", "Sash tilted: 0 closed or turned (gravity in the sash plane); 1 in Study 3"},
      {"t_chk", "0", "Age (years) at which the stationary studies take wear and set: 0 new; Studies 3 and 4 sweep 0 and 10"},
      {"n_turn", "3", "Full openings (turn) per day, all year: airing by opening wide 2 to 4 times a day (UBA advice)"},
      {"t_turn_w", "5[min]", "Duration of one full opening at midwinter (UBA advice: 5 to 10 min in winter)"},
      {"t_turn_s", "20[min]", "Duration of one full opening at midsummer (UBA advice: 20 to 30 min in summer)"},
      {"n_tilt_s", "1", "Tilt openings per day at midsummer, none at midwinter (long tilting is advised against in the cold season): ASSUMPTION"},
      {"h_tilt", "6[h]", "Duration of one tilt opening (e.g. overnight in summer): ASSUMPTION"},
      {"a_tilt", "0.12[m]", "Tilt opening at the sash top, set by the scissor arm: ASSUMPTION (typically 10 to 15 cm)"},
      {"th_tilt", "asin(a_tilt/H_sa)", "Tilt angle of the sash"},
      {"r_pin", "6[mm]", "Pin radius of the corner bearing and the scissor bearing: ASSUMPTION"},
      {"s_arm", "a_tilt", "Sliding of the scissor arm in its guide per tilt opening (about the tilt opening)"},
      {"f_use", "1", "Use of the window: 1 opened and tilted as set above (default); 0 never opened (Study 5)"},
      {"f_turn_t", "f_use*n_turn*(t_turn_w+t_turn_s)/2/1[d]", "Share of time fully open (year average)"},
      {"f_tilt_t", "f_use*n_tilt_s/2*h_tilt/1[d]", "Share of time tilted (year average: tilting follows the warm-season weight, mean 1/2)"},
      {"f_closed_t", "1-f_turn_t-f_tilt_t", "Share of time closed"},
      {"phi_sg", "f_closed_t", "Stop gasket: time-averaged load / load when closed (compressed only when closed)"},
      {"phi_lk", "f_closed_t", "Locks: time-averaged load / load when closed (they press only when closed)"},
      {"phi_cb", "f_closed_t+f_turn_t+f_tilt_t*cos(th_tilt)/2", "Corner bearing: full sash weight closed and turned, half of it when tilted"},
      {"phi_hs", "f_closed_t+f_turn_t", "Hinge side block and scissor: cantilever couple closed and turned, none when tilted (both bottom corners bear)"},
      {"phi_tb", "f_tilt_t+f_lifter*f_closed_t", "Tilt bearing: loaded when tilted (and when closed if a sash lifter is fitted)"},
      {"tau_set", "3", "Setting blocks: time constant of their permanent set (years): 63 % after tau_set"},
      {"n_cyc", "n_turn+n_tilt_s/2", "Openings and closings per day, year average (turn + tilt)"},
      {"N_ref", "20000", "Cycles at which the wear values below are reached: EN 12400 durability class 3 (class 2: 10 000)"},
      {"dv_cb_inf", "0.8[mm]", "Corner bearing sinking after N_ref turn cycles (pin and bushing wear): ASSUMPTION to calibrate"},
      {"dh_ss_inf", "0.8[mm]", "Scissor stay play after N_ref tilt cycles (sash top moves away from the jamb): ASSUMPTION"},
      {"d_sb_inf", "0.3[mm]", "Final permanent set of each load-bearing setting block: ASSUMPTION"},
      {"E_lk_ip", "0.2[MPa]*f_lock", "Locks: in-plane stiffness (cams slide in their keepers)"},
      {"G_lk_ip", "0.1[MPa]*f_lock", "Locks: in-plane shear stiffness"},
      {"t_inst", "0", "Season of installation: 0 winter, 0.25 spring, 0.5 summer, 0.75 autumn"},
      {"T_mean", "9.5[degC]", "Annual mean outdoor temperature"},
      {"A_seas", "9[K]", "Seasonal amplitude (coldest at whole years)"},
      {"T_adj", "18[degC]", "Neighbour rooms"},
      {"e_left", "0", "Left wall exposed to outdoor (1) or neighbour (0)"},
      {"e_right", "0", "Right wall exposed (1) or neighbour (0)"},
      {"e_back", "0", "Back wall exposed (1) or neighbour (0)"},
      {"e_up", "0", "Ceiling top exposed (1) or room above (0)"},
      {"e_down", "0", "Floor bottom exposed (1) or room below (0)"},
      {"dT_plume", "0[K]", "Extra air temperature at the window from the heater"},
      {"h_in", "7.69[W/(m^2*K)]", "Indoor surface coefficient (Rsi 0.13)"},
      {"h_out", "25[W/(m^2*K)]", "Outdoor surface coefficient (Rse 0.04)"},
      {"h_heater", "25[W/(m^2*K)]", "Heater surface coefficient"},
      {"h_gap", "6[W/(m^2*K)]", "Heater-to-wall exchange across the gap"},
      {"q_vol", "31.25[W/m^3]", "Specific heating power at the design point"},
      {"P_heat", "q_vol*W_in*H_in*L_in", "Design heater power: 31.25 W/m^3 x 48 m^3 = 1500 W (user input)"},
      {"f_sun", "0", "Design day: sun on (1) / off (0)"},
      {"G_sun", "400[W/m^2]", "Design-day irradiance on the facade"},
      {"G_mean", "127[W/m^2]", "Daily-mean irradiance (aging study): all-hours mean on a horizontal surface in Germany 2024; a vertical facade receives less"},
      {"G_t", "(1-f_season)*f_sun*G_sun+f_season*G_mean", "Irradiance used"},
      {"a_wall", "0.6", "Solar absorptance, facade"},
      {"a_frame", "0.7", "Solar absorptance, frame and sash"},
      {"a_igu", "0.15", "Solar absorptance, glazing"},
      {"E_con", "31[GPa]", "Concrete C25/30 modulus Ecm = 22000*(fcm/10)^0.3 MPa with fcm = 33 MPa, EN 1992-1-1 Table 3.1"},
      {"rho_con", "25[kN/m^3]/g_const", "Reinforced concrete weight 25 kN/m^3, EN 1991-1-1 Annex A (= 2549 kg/m^3)"},
      {"k_con", "2.1[W/(m*K)]", "Reinforced concrete conductivity (German building typology data, IWU via RWTH hub4lca)"},
      {"f_k_mas", "3[MPa]", "Masonry characteristic compressive strength: ASSUMPTION (test per EN 1052-1)"},
      {"E_mas", "1000*f_k_mas", "Masonry modulus E = K_E*f_k with K_E = 1000, EN 1996-1-1 3.7.2"},
      {"U_wall", "0.8[W/(m^2*K)]", "Wall U-value"},
      {"k_wall", "t_w/(1/U_wall-0.17[m^2*K/W])", "Effective wall conductivity"},
      {"Uf", "1.4[W/(m^2*K)]", "Frame/sash U-value"},
      {"k_frame", "d_f/(1/Uf-0.17[m^2*K/W])", "Effective frame/sash conductivity"},
      {"Ug", "1.1[W/(m^2*K)]", "Glazing U-value"},
      {"k_igu", "t_igu/(1/Ug-0.17[m^2*K/W])", "Effective glazing conductivity"},

      // stiffness, mass and aging
      {"I_fr", "20[cm^4]", "Frame profile moment of inertia (datasheet)"},
      {"I_sa", "25[cm^4]", "Sash profile moment of inertia (datasheet)"},
      {"E_fr", "70[GPa]*I_fr/(b_fr*d_f^3/12)", "Equivalent frame modulus"},
      {"E_sa", "70[GPa]*I_sa/(b_sa*d_f^3/12)", "Equivalent sash modulus"},
      {"rho_fr", "1.5[kg/m]/(b_fr*d_f+(s_gap+o_st)*t_lip)", "Equivalent frame density (profile incl. stop lip: 1.5 kg/m)"},
      {"rho_sa", "1.8[kg/m]/(b_sa*d_f)", "Equivalent sash density"},
      {"E_igu", "70[GPa]*2*t_g/t_igu", "Equivalent glazing modulus"},
      {"rho_igu", "2500[kg/m^3]*2*t_g/t_igu", "Equivalent glazing density"},
      // statics of the sash in its open states: these weight the wear of a tilt against a turn (Archard: force x sliding)
      {"m_sa_e", "rho_sa*((W_sa*H_sa-W_c*H_c)*d_f-(W_s*H_s-W_c*H_c)*t_s)", "Sash mass, from the model geometry"},
      {"m_gl_e", "rho_igu*W_g*H_g*t_igu", "Glazing mass, from the model geometry"},
      {"W_sg", "(m_sa_e+m_gl_e)*g_const", "Weight of sash and glass"},
      {"H_cpl", "W_sg*(W_sa/2)/(H_sa-L_hw)", "Turned open: horizontal couple on the two hinges (the sash hangs as a cantilever)"},
      {"F_cbr_turn", "sqrt(W_sg^2+H_cpl^2)", "Corner bearing force, turned open (statics)"},
      {"F_cbr_tilt", "W_sg*cos(th_tilt)/2", "Corner bearing force, tilted: the weight is shared with the tilt bearing (statics)"},
      {"F_ssr_turn", "H_cpl", "Scissor bearing force, turned open (statics)"},
      {"F_ssr_tilt", "W_sg*sin(th_tilt)/2", "Scissor arm force, tilted: it holds the sash top (statics)"},
      {"w_cb_tilt", "F_cbr_tilt*th_tilt/(F_cbr_turn*pi/2)", "Corner bearing wear of one tilt relative to one turn (pin turns th_tilt instead of 90 deg)"},
      {"w_ss_turn", "F_ssr_turn*r_pin*pi/2/(F_ssr_tilt*s_arm)", "Scissor wear of one turn relative to one tilt (pin turns 90 deg instead of the arm sliding s_arm)"},
      {"E0_gk", "1[MPa]", "Centre gasket: takes part in the heat transfer only (no load path in the model)"},
      {"F_gk", "50[N/m]", "Stop gasket: linear compression force over its working range, EN 12365-1 grades 3 to 4 (20 to 100 N/m) (ASSUMPTION)"},
      {"c_gk", "3[mm]", "Stop gasket working range, EN 12365-1 grade 3 (2 to 4 mm) (ASSUMPTION)"},
      {"c0_gk", "2[mm]", "Stop gasket compression with the window closed and locked (ASSUMPTION)"},
      {"k_gk", "F_gk/c_gk", "Stop gasket line stiffness (force per metre of gasket per metre of compression)"},
      {"E0_sg", "k_gk*t_sg/o_st*f_stopg", "Stop gasket equivalent modulus: a soft layer that presses but carries almost no shear"},
      {"f_gkp", "1", "Closing pressure of the stop gasket: 1 on, 0 off"},
      {"E0_bed", "5[MPa]", "Bedding EPDM, initial"},
      {"E0_jt", "3[MPa]", "PU foam joint incl. anchors, initial"},
      {"E0_hw", "2[GPa]", "Hinge with bushing, initial"},
      {"E0_lk", "0.5[GPa]*f_lock", "Locking point, initial (f_lock: disengaged when the sash is open)"},
      {"E0_tb", "E0_hw*f_tb", "Tilt bearing / sash lifter at the bottom lock-side corner, initial (f_tb: engaged or not)"},
      {"G_lk_z", "E0_lk/(2*(1+0.3))", "Locks: out-of-plane shear stiffness (they press the sash onto the gasket)"},
      {"E0_sbl", "1[GPa]", "Load-bearing setting blocks, initial"},
      {"E0_sbd", "0.2[GPa]", "Distance blocks, initial"},
      {"r_epdm", "0.6", "EPDM long-term / initial modulus"},
      {"tau_epdm", "0.5", "EPDM relaxation time (years): viscoelastic branch"},
      {"r_foam", "0.5", "PU foam long-term / initial"},
      {"tau_foam", "1", "PU foam relaxation time (years)"},
      {"r_hw", "0.5", "Hinge bushing long-term / initial (incl. wear)"},
      {"tau_hw", "2", "Hinge relaxation time (years)"},
      // effective long-term ratios under realistic use: a part loaded only part of the time relaxes less (it rests while unloaded)
      {"r_sg_eff", "1-phi_sg*(1-r_epdm)", "Stop gasket: long-term / initial under realistic use"},
      {"r_lk_eff", "1-phi_lk*(1-r_hw)", "Locks: long-term / initial under realistic use"},
      {"r_cb_eff", "1-phi_cb*(1-r_hw)", "Corner bearing (bottom block): long-term / initial under realistic use"},
      {"r_hs_eff", "1-phi_hs*(1-r_hw)", "Hinge side block and scissor: long-term / initial under realistic use"},
      {"r_tb_eff", "1-phi_tb*(1-r_hw)", "Tilt bearing: long-term / initial under realistic use"},
      {"r_sb", "0.7", "Setting blocks long-term / initial"},
      {"tau_sb", "2", "Setting block relaxation time (years)"},
      {"t_shell", "0.5", "Years between loading the structure (shell finished) and installing the window"},
      {"phi0_con", "2.65", "Concrete notional creep coefficient, EN 1992-1-1 B.2: C25/30, RH 50 %, h0 = 200 mm, loaded at 28 days"},
      {"bH_con", "550", "Concrete: beta_H in days, EN 1992-1-1 B.8 (h0 = 200 mm, RH 50 %)"},
      {"phi_mas", "1.0", "Masonry final creep coefficient, EN 1996-1-1 Table 3.8 (clay units 0.5 to 1.5)"},
      {"bH_mas", "700", "Masonry: beta_H in days for the EN 1992 time shape (h0 = 300 mm): ASSUMPTION"},
      {"tauc_cr", "1.5", "Creep retardation time of the branch (years): exact at 10 years; years 1 to 10 within 10 % of EN 1992 for concrete, 12 % for masonry (at most 0.06 in phi)"},
      {"bc0_con", "(t_shell*365.25/(bH_con+t_shell*365.25))^0.3", "Concrete: share of the final creep reached at installation (EN 1992 beta_c)"},
      {"bc10_con", "((t_shell+10)*365.25/(bH_con+(t_shell+10)*365.25))^0.3", "Concrete: share reached 10 years after installation"},
      {"phi_con_inst", "phi0_con*bc0_con", "Concrete creep coefficient already reached at installation"},
      {"dphi_con_10", "phi0_con*(bc10_con-bc0_con)", "Concrete creep still to come in the 10 years after installation"},
      {"dphi_con", "dphi_con_10/(1-exp(-10/tauc_cr))", "Concrete: creep strength of the branch (final creep after installation)"},
      {"r_con", "1/(1+dphi_con)", "Concrete: long-term / instantaneous modulus under a lasting load"},
      {"bc0_mas", "(t_shell*365.25/(bH_mas+t_shell*365.25))^0.3", "Masonry: share of the final creep reached at installation"},
      {"bc10_mas", "((t_shell+10)*365.25/(bH_mas+(t_shell+10)*365.25))^0.3", "Masonry: share reached 10 years after installation"},
      {"phi_mas_inst", "phi_mas*bc0_mas", "Masonry creep coefficient already reached at installation"},
      {"dphi_mas_10", "phi_mas*(bc10_mas-bc0_mas)", "Masonry creep still to come in the 10 years after installation"},
      {"dphi_mas", "dphi_mas_10/(1-exp(-10/tauc_cr))", "Masonry: creep strength of the branch"},
      {"r_mas", "1/(1+dphi_mas)", "Masonry: long-term / instantaneous modulus under a lasting load"},

      // loads
      {"f_dead", "1", "Building self-weight and dead load: 1 in the aging study (default), 0 on the design day"},
      {"p_roof", "12[kPa]", "Dead load of the storeys above and the roof per m2 of floor; it comes down through the walls"},
      {"p_roof_w", "p_roof*(W_in+2*t_w)*(L_in+2*t_w)/((W_in+2*t_w)*(L_in+2*t_w)-W_in*L_in)", "The same dead load spread over the wall tops only"},
      {"h_up", "H_in/2", "Walls of the storey above, modelled to their mid-height: a continuous wall bends through zero there, so it is held horizontally only"},
      {"rho_mas", "1400[kg/m^3]", "Masonry density (ASSUMPTION, perforated brick)"},
      {"p_roof_top", "p_roof_w-rho_mas*g_const*h_up", "Dead load at the top of those walls (their own lower half already acts as weight)"},
      {"f_live", "1", "Live load factor"},
      {"psi2_live", "0.3", "Quasi-permanent share of the live load in the aging study, EN 1990 Table A1.1 (categories A, B)"},
      {"p_live", "3[kPa]", "Live load of the floor above (incl. partitions), on the ceiling slab span"},
      {"f_wind", "0", "Wind on (1) / off (0)"},
      {"p_wind", "0.5[kPa]", "Wind pressure on the window"}
    };
    for (String[] p : P) model.param().set(p[0], p[1], p[2]);

    // ===================================================================== geometry
    model.component().create(C, true);
    model.component(C).label("Building and window");
    model.component(C).geom().create("geom1", 3);
    model.component(C).mesh().create("mesh1");

    blk(model, "b_out", "Outer envelope", new String[]{"-t_w", "-t_slab", "-(L_in+2*t_w)"}, new String[]{"W_in+2*t_w", "H_in+2*t_slab", "L_in+2*t_w"});
    blk(model, "b_room", "Room air (removed)", new String[]{"0", "0", "-(t_w+L_in)"}, new String[]{"W_in", "H_in", "L_in"});
    blk(model, "b_open", "Wall opening", new String[]{"xo0", "yo0", "-t_w-0.05[m]"}, new String[]{"W_o", "H_o", "t_w+0.10[m]"});
    dif(model, "shell", "Walls and slabs", "b_out", new String[]{"b_room", "b_open"});
    blk(model, "p_floor", "Partition: floor slab", new String[]{"-t_w", "-t_slab", "-(L_in+2*t_w)"}, new String[]{"W_in+2*t_w", "t_slab", "L_in+2*t_w"});
    blk(model, "p_ceil", "Partition: ceiling slab", new String[]{"-t_w", "H_in", "-(L_in+2*t_w)"}, new String[]{"W_in+2*t_w", "t_slab", "L_in+2*t_w"});
    blk(model, "p_ceil_in", "Partition: ceiling slab above the room (the rest lies on the walls)", new String[]{"0", "H_in", "-(t_w+L_in)"}, new String[]{"W_in", "t_slab", "L_in"});
    if (ADD_UPPER_WALL) {
      // walls of the storey above, up to their mid-height (four blocks, so each top face can be held across its own wall)
      blk(model, "up_f", "Storey above: facade wall to mid-height", new String[]{"-t_w", "H_in+t_slab", "-t_w"}, new String[]{"W_in+2*t_w", "h_up", "t_w"});
      blk(model, "up_b", "Storey above: back wall to mid-height", new String[]{"-t_w", "H_in+t_slab", "-(L_in+2*t_w)"}, new String[]{"W_in+2*t_w", "h_up", "t_w"});
      blk(model, "up_l", "Storey above: left wall to mid-height", new String[]{"-t_w", "H_in+t_slab", "-(L_in+t_w)"}, new String[]{"t_w", "h_up", "L_in"});
      blk(model, "up_r", "Storey above: right wall to mid-height", new String[]{"W_in", "H_in+t_slab", "-(L_in+t_w)"}, new String[]{"t_w", "h_up", "L_in"});
    }
    if (ADD_SHUTTER_BOX) {
      blk(model, "p_lintel", "Partition: lintel (outer part)", new String[]{"xo0-l_bear", "yo0+H_o", "-t_w+d_sbox"}, new String[]{"W_o+2*l_bear", "h_lin", "t_w-d_sbox"});
      blk(model, "p_sbox", "Partition: roller-shutter box", new String[]{"xo0-0.05[m]", "yo0+H_o", "-t_w"}, new String[]{"W_o+0.10[m]", "h_sbox", "d_sbox"});
    } else {
      blk(model, "p_lintel", "Partition: concrete lintel", new String[]{"xo0-l_bear", "yo0+H_o", "-t_w"}, new String[]{"W_o+2*l_bear", "h_lin", "t_w"});
    }
    // installation joint
    blk(model, "b_jo", "Joint envelope", new String[]{"xo0", "yo0", "zf0"}, new String[]{"W_o", "H_o", "d_f"});
    blk(model, "b_fe1", "Frame envelope (for joint)", new String[]{"xf0", "yf0", "zf0"}, new String[]{"W_f", "H_f", "d_f"});
    dif(model, "jt", "Installation joint", "b_jo", new String[]{"b_fe1"});
    // fixed frame
    blk(model, "b_fe2", "Frame envelope", new String[]{"xf0", "yf0", "zf0"}, new String[]{"W_f", "H_f", "d_f"});
    blk(model, "b_fi", "Frame inner opening, room part (up to the stop lip)", new String[]{"xfi0", "yfi0", "zf0-0.01[m]"}, new String[]{"W_fi", "H_fi", "d_f-t_lip+0.01[m]"});
    blk(model, "b_fi_st", "Frame inner opening inside the stop lip", new String[]{"xsa0+o_st", "ysa0+o_st", "zf0-0.01[m]"}, new String[]{"W_sa-2*o_st", "H_sa-2*o_st", "d_f+0.02[m]"});
    dif(model, "frame", "Fixed frame (Blendrahmen) with outer stop lip", "b_fe2", new String[]{"b_fi", "b_fi_st"});
    // sash
    blk(model, "b_se", "Sash envelope (stands dz_sa proud on the room side)", new String[]{"xsa0", "ysa0", "zsr0"}, new String[]{"W_sa", "H_sa", "d_f"});
    blk(model, "b_cl", "Clear opening", new String[]{"xc0", "yc0", "zsr0-0.01[m]"}, new String[]{"W_c", "H_c", "d_f+0.02[m]"});
    blk(model, "b_sl", "Sash rebate", new String[]{"xs0", "ys0", "zs0"}, new String[]{"W_s", "H_s", "t_s"});
    dif(model, "sash", "Sash (Fluegel)", "b_se", new String[]{"b_cl", "b_sl"});
    // glazing in the sash
    blk(model, "b_sl2", "Sash rebate (for bedding)", new String[]{"xs0", "ys0", "zs0"}, new String[]{"W_s", "H_s", "t_s"});
    blk(model, "b_ig2", "Glazing (for bedding)", new String[]{"xg0", "yg0", "zg0"}, new String[]{"W_g", "H_g", "t_igu"});
    blk(model, "b_cl2", "Clear opening (for bedding)", new String[]{"xc0", "yc0", "zsr0-0.01[m]"}, new String[]{"W_c", "H_c", "d_f+0.02[m]"});
    blk(model, "b_eb", "Edge clearance band (removed: air)", new String[]{"xs0", "ys0", "zg0"}, new String[]{"W_s", "H_s", "t_igu"});
    dif(model, "bed", "EPDM bedding on both glass faces", "b_sl2", new String[]{"b_ig2", "b_cl2", "b_eb"});
    blk(model, "igu", "Glazing 4-14-4", new String[]{"xg0", "yg0", "zg0"}, new String[]{"W_g", "H_g", "t_igu"});
    blk(model, "sbl1", "Load block, bottom hinge side", new String[]{"xg0+a_sb", "ys0", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbl2", "Load block, top lock side", new String[]{"xg0+W_g-a_sb-L_sb", "yg0+H_g", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbd1", "Distance block, bottom lock side", new String[]{"xg0+W_g-a_sb-L_sb", "ys0", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbd2", "Distance block, top hinge side", new String[]{"xg0+a_sb", "yg0+H_g", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    // centre gasket ring in the rebate gap
    blk(model, "b_gk1", "Gasket band outer", new String[]{"xfi0", "yfi0", "z_fc-d_gk/2"}, new String[]{"W_fi", "H_fi", "d_gk"});
    blk(model, "b_gk2", "Gasket band inner", new String[]{"xsa0", "ysa0", "z_fc-d_gk/2-0.01[m]"}, new String[]{"W_sa", "H_sa", "d_gk+0.02[m]"});
    dif(model, "gasket", "Centre gasket (EPDM)", "b_gk1", new String[]{"b_gk2"});
    // stop gasket: the sash closes outward against the frame stop lip (inward-opening turn-tilt window)
    blk(model, "b_sg1", "Stop gasket band outer", new String[]{"xsa0", "ysa0", "zsr0+d_f"}, new String[]{"W_sa", "H_sa", "t_sg"});
    blk(model, "b_sg2", "Stop gasket band inner", new String[]{"xsa0+o_st", "ysa0+o_st", "zsr0+d_f-0.01[m]"}, new String[]{"W_sa-2*o_st", "H_sa-2*o_st", "t_sg+0.02[m]"});
    dif(model, "sgasket", "Stop gasket (EPDM) between sash and frame stop", "b_sg1", new String[]{"b_sg2"});
    // decorative handle on the room side of the lock-side sash member (marks the inside; light and soft)
    blk(model, "hd_r", "Handle rosette", new String[]{"x_hd-0.015[m]", "y_hd-0.036[m]", "zsr0-0.010[m]"}, new String[]{"0.030[m]", "0.072[m]", "0.010[m]"});
    blk(model, "hd_n", "Handle neck", new String[]{"x_hd-0.008[m]", "y_hd+0.010[m]", "zsr0-0.045[m]"}, new String[]{"0.016[m]", "0.016[m]", "0.035[m]"});
    blk(model, "hd_l", "Handle lever (pointing down: locked)", new String[]{"x_hd-0.009[m]", "y_hd-0.104[m]", "zsr0-0.059[m]"}, new String[]{"0.018[m]", "0.130[m]", "0.014[m]"});
    model.component(C).geom("geom1").create("handle", "Union");
    model.component(C).geom("geom1").feature("handle").label("Window handle (decorative)");
    model.component(C).geom("geom1").feature("handle").selection("input").set(new String[]{"hd_r", "hd_n", "hd_l"});
    model.component(C).geom("geom1").feature("handle").set("intbnd", false);
    // hinges on the left (room-side hardware zone)
    blk(model, "hw1", "Corner bearing, bottom", new String[]{"xsa0", "yfi0", "zf0"}, new String[]{"L_hw", "s_gap", "d_hw"});
    blk(model, "hw2", "Corner bearing, side", new String[]{"xfi0", "ysa0", "zf0"}, new String[]{"s_gap", "L_hw", "d_hw"});
    blk(model, "hw3", "Scissor stay, top", new String[]{"xfi0", "ysa0+H_sa-L_hw", "zf0"}, new String[]{"s_gap", "L_hw", "d_hw"});
    // tilt bearing (Kipplager) at the bottom lock-side corner: carries the sash when tilted (or a sash lifter when closed)
    blk(model, "tb", "Tilt bearing / sash lifter, bottom lock side", new String[]{"xsa0+W_sa-L_hw", "yfi0", "zf0"}, new String[]{"L_hw", "s_gap", "d_hw"});
    // locking points
    blk(model, "lk1", "Lock, right lower", new String[]{"xsa0+W_sa", "ysa0+H_sa/4-L_lk/2", "zf0"}, new String[]{"s_gap", "L_lk", "d_hw"});
    blk(model, "lk2", "Lock, right upper", new String[]{"xsa0+W_sa", "ysa0+3*H_sa/4-L_lk/2", "zf0"}, new String[]{"s_gap", "L_lk", "d_hw"});
    blk(model, "lk3", "Lock, top middle", new String[]{"xsa0+W_sa/2-L_lk/2", "ysa0+H_sa", "zf0"}, new String[]{"L_lk", "s_gap", "d_hw"});
    blk(model, "lk4", "Lock, bottom middle", new String[]{"xsa0+W_sa/2-L_lk/2", "yfi0", "zf0"}, new String[]{"L_lk", "s_gap", "d_hw"});
    // heater
    blk(model, "heater", "Heater", new String[]{"W_in/2-L_h/2", "y_h", "-t_w-g_h-D_h"}, new String[]{"L_h", "H_h", "D_h"});
    blk(model, "gap", "Air gap heater-wall (thermal only)", new String[]{"W_in/2-L_h/2", "y_h", "-t_w-g_h"}, new String[]{"L_h", "H_h", "g_h"});
    model.component(C).geom("geom1").run();

    // ===================================================================== domain selections
    box(model, "s_all", "All domains", 3, "-10", "10", "-10", "10", "-20", "10");
    box(model, "s_heater", "Heater", 3, "W_in/2-L_h/2-tol", "W_in/2+L_h/2+tol", "y_h-tol", "y_h+H_h+tol", "-t_w-g_h-D_h-tol", "-t_w-g_h+tol");
    box(model, "s_gap", "Heater air gap", 3, "W_in/2-L_h/2-tol", "W_in/2+L_h/2+tol", "y_h-tol", "y_h+H_h+tol", "-t_w-g_h-tol", "-t_w+tol");
    box(model, "s_floor", "Floor slab", 3, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "s_ceil", "Ceiling slab", 3, "-t_w-tol", "W_in+t_w+tol", "H_in-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    if (ADD_SHUTTER_BOX) {
      box(model, "s_lintel", "Lintel", 3, "xo0-l_bear-tol", "xo0+W_o+l_bear+tol", "yo0+H_o-tol", "yo0+H_o+h_lin+tol", "-t_w+d_sbox-tol", "tol");
      box(model, "s_sbox", "Roller-shutter box", 3, "xo0-0.05[m]-tol", "xo0+W_o+0.05[m]+tol", "yo0+H_o-tol", "yo0+H_o+h_sbox+tol", "-t_w-tol", "-t_w+d_sbox+tol");
    } else {
      box(model, "s_lintel", "Lintel", 3, "xo0-l_bear-tol", "xo0+W_o+l_bear+tol", "yo0+H_o-tol", "yo0+H_o+h_lin+tol", "-t_w-tol", "tol");
    }
    suni(model, "s_concrete", "Concrete (slabs and lintel)", 3, new String[]{"s_floor", "s_ceil", "s_lintel"});
    box(model, "s_fenv", "Window (frame envelope)", 3, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "zsr0-tol", "zf0+d_f+tol");
    box(model, "s_fin", "Inside the frame opening", 3, "xfi0-tol", "xfi0+W_fi+tol", "yfi0-tol", "yfi0+H_fi+tol", "zsr0-tol", "zf0+d_f+tol");
    sdiff(model, "s_frame", "Fixed frame", 3, new String[]{"s_fenv"}, new String[]{"s_fin"});
    box(model, "s_senv", "Sash envelope", 3, "xsa0-tol", "xsa0+W_sa+tol", "ysa0-tol", "ysa0+H_sa+tol", "zsr0-tol", "zsr0+d_f+tol");
    sdiff(model, "s_gapparts", "Rebate gap parts", 3, new String[]{"s_fin"}, new String[]{"s_senv"});
    box(model, "s_hwz", "Hardware zone", 3, "xfi0-tol", "xfi0+W_fi+tol", "yfi0-tol", "yfi0+H_fi+tol", "zf0-tol", "zf0+d_hw+tol");
    sdiff(model, "s_hw_all", "All hardware", 3, new String[]{"s_hwz"}, new String[]{"s_senv"});
    box(model, "s_hinge_z", "Hinge strip (left)", 3, "xfi0-tol", "xsa0+L_hw+tol", "yfi0-tol", "yfi0+H_fi+tol", "zf0-tol", "zf0+d_hw+tol");
    box(model, "s_tb", "Tilt bearing / sash lifter (bottom lock side)", 3, "xsa0+W_sa-L_hw-tol", "xsa0+W_sa+tol", "yfi0-tol", "yfi0+s_gap+tol", "zf0-tol", "zf0+d_hw+tol");
    sdiff(model, "s_lock", "Locking points", 3, new String[]{"s_hw_all"}, new String[]{"s_hinge_z", "s_tb"});
    sdiff(model, "s_hinge", "Hinges", 3, new String[]{"s_hw_all"}, new String[]{"s_lock", "s_tb"});
    sdiff(model, "s_gasket", "Gaskets (centre and stop)", 3, new String[]{"s_gapparts"}, new String[]{"s_hw_all"});
    box(model, "s_gk_stop", "Stop gasket", 3, "xsa0-tol", "xsa0+W_sa+tol", "ysa0-tol", "ysa0+H_sa+tol", "zsr0+d_f-tol", "z_lip0+tol");
    sdiff(model, "s_gk_ctr", "Centre gasket (heat transfer only)", 3, new String[]{"s_gasket"}, new String[]{"s_gk_stop"});
    box(model, "s_slot", "Sash rebate content", 3, "xs0-tol", "xs0+W_s+tol", "ys0-tol", "ys0+H_s+tol", "zs0-tol", "zs0+t_s+tol");
    sdiff(model, "s_sash", "Sash", 3, new String[]{"s_senv"}, new String[]{"s_slot"});
    box(model, "s_igu", "Glazing", 3, "xg0-tol", "xg0+W_g+tol", "yg0-tol", "yg0+H_g+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "s_sbl1", "Load block 1", 3, "xg0+a_sb-tol", "xg0+a_sb+L_sb+tol", "ys0-tol", "yg0+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "s_sbl2", "Load block 2", 3, "xg0+W_g-a_sb-L_sb-tol", "xg0+W_g-a_sb+tol", "yg0+H_g-tol", "ys0+H_s+tol", "zg0-tol", "zg0+t_igu+tol");
    suni(model, "s_sbl", "Load-bearing setting blocks", 3, new String[]{"s_sbl1", "s_sbl2"});
    box(model, "s_sbd1", "Distance block 1", 3, "xg0+W_g-a_sb-L_sb-tol", "xg0+W_g-a_sb+tol", "ys0-tol", "yg0+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "s_sbd2", "Distance block 2", 3, "xg0+a_sb-tol", "xg0+a_sb+L_sb+tol", "yg0+H_g-tol", "ys0+H_s+tol", "zg0-tol", "zg0+t_igu+tol");
    suni(model, "s_sbd", "Distance blocks", 3, new String[]{"s_sbd1", "s_sbd2"});
    box(model, "s_cb_bot", "Corner bearing, bottom block (sinks)", 3, "xsa0-tol", "xsa0+L_hw+tol", "yfi0-tol", "yfi0+s_gap+tol", "zf0-tol", "zf0+d_hw+tol");
    box(model, "s_ss_top", "Scissor stay block (play)", 3, "xfi0-tol", "xfi0+s_gap+tol", "ysa0+H_sa-L_hw-tol", "ysa0+H_sa+tol", "zf0-tol", "zf0+d_hw+tol");
    box(model, "s_cb_side", "Corner bearing, side block", 3, "xfi0-tol", "xfi0+s_gap+tol", "ysa0-tol", "ysa0+L_hw+tol", "zf0-tol", "zf0+d_hw+tol");
    sdiff(model, "s_hinge_rest", "Hinge side block and scissor", 3, new String[]{"s_hinge"}, new String[]{"s_cb_bot"});
    sdiff(model, "s_bed", "EPDM bedding", 3, new String[]{"s_slot"}, new String[]{"s_igu", "s_sbl", "s_sbd"});
    box(model, "s_jbox", "Opening at frame depth", 3, "xo0-tol", "xo0+W_o+tol", "yo0-tol", "yo0+H_o+tol", "zsr0-tol", "zf0+d_f+tol");
    sdiff(model, "s_joint", "Installation joint", 3, new String[]{"s_jbox"}, new String[]{"s_fenv"});
    sdiff(model, "s_mech", "Structural domains (sash and frame meet only at hinges, locks and the soft stop gasket)", 3, new String[]{"s_all"}, new String[]{"s_heater", "s_gap", "s_gk_ctr"});
    box(model, "s_handle", "Window handle (decorative)", 3, "x_hd-0.015[m]-tol", "x_hd+0.015[m]+tol", "y_hd-0.104[m]-tol", "y_hd+0.036[m]+tol", "zsr0-0.059[m]-tol", "zsr0+tol");
    suni(model, "s_window", "Window incl. handle", 3, new String[]{"s_fenv", "s_handle"});
    suni(model, "s_sashgrp", "Sash with glass and handle (turns and tilts with the sash)", 3, new String[]{"s_senv", "s_handle"});
    sdiff(model, "s_win_mech", "Window incl. handle, structural parts", 3, new String[]{"s_window"}, new String[]{"s_gk_ctr"});
    suni(model, "s_meshwin", "Window region for the mesh", 3, new String[]{"s_jbox", "s_handle"});
    sdiff(model, "s_bldg", "Building structure (no window)", 3, new String[]{"s_mech"}, new String[]{"s_fenv", "s_handle"});
    if (ADD_SHUTTER_BOX)
      sdiff(model, "s_masonry", "Masonry walls", 3, new String[]{"s_bldg"}, new String[]{"s_concrete", "s_joint", "s_sbox"});
    else
      sdiff(model, "s_masonry", "Masonry walls", 3, new String[]{"s_bldg"}, new String[]{"s_concrete", "s_joint"});

    // ===================================================================== boundary selections
    model.component(C).selection().create("b_ext_all", "Adjacent");
    model.component(C).selection("b_ext_all").label("All outer boundaries, incl. the edge-clearance void");
    model.component(C).selection("b_ext_all").set("input", new String[]{"s_all"});
    model.component(C).selection("b_ext_all").set("outputdim", 2);
    model.component(C).selection("b_ext_all").set("interior", false);
    // the 5 mm edge clearance is a closed air void: its faces get no air temperature, sun or wind (free and insulated)
    box(model, "b_ec_l", "Edge clearance, left", 2, "xs0-tol", "xg0+tol", "ys0-tol", "ys0+H_s+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "b_ec_r", "Edge clearance, right", 2, "xg0+W_g-tol", "xs0+W_s+tol", "ys0-tol", "ys0+H_s+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "b_ec_b", "Edge clearance, bottom", 2, "xs0-tol", "xs0+W_s+tol", "ys0-tol", "yg0+tol", "zg0-tol", "zg0+t_igu+tol");
    box(model, "b_ec_t", "Edge clearance, top", 2, "xs0-tol", "xs0+W_s+tol", "yg0+H_g-tol", "ys0+H_s+tol", "zg0-tol", "zg0+t_igu+tol");
    suni(model, "b_ec", "Edge clearance void faces", 2, new String[]{"b_ec_l", "b_ec_r", "b_ec_b", "b_ec_t"});
    sdiff(model, "b_ext", "All outer boundaries (exposed to air outside or in the room)", 2, new String[]{"b_ext_all"}, new String[]{"b_ec"});
    model.component(C).selection().create("b_heat", "Adjacent");
    model.component(C).selection("b_heat").label("Heater surfaces");
    model.component(C).selection("b_heat").set("input", new String[]{"s_heater"});
    model.component(C).selection("b_heat").set("outputdim", 2);
    model.component(C).selection("b_heat").set("interior", false);
    box(model, "b_roombox", "Room side region", 2, "-tol", "W_in+tol", "-tol", "H_in+tol", "-(t_w+L_in)-tol", "z_fc");
    sint(model, "b_room", "Room side surfaces", 2, new String[]{"b_ext", "b_roombox"});
    box(model, "b_winbox_in", "Window region, room side", 2, "xo0-tol", "xo0+W_o+tol", "yo0-tol", "yo0+H_o+tol", "-t_w-tol", "z_fc");
    sint(model, "b_win_in", "Window, reveal and rebate, room side", 2, new String[]{"b_ext", "b_winbox_in"});
    sdiff(model, "b_in", "Indoor surfaces (no heater, no window)", 2, new String[]{"b_room"}, new String[]{"b_heat", "b_win_in"});
    box(model, "b_outbox", "Facade region", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+h_up+tol", "z_fc", "tol");
    sint(model, "b_out", "Outdoor surfaces", 2, new String[]{"b_ext", "b_outbox"});
    box(model, "b_winbox_out", "Window region, outside", 2, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "z_fc", "zf0+d_f+tol");
    sint(model, "b_win_out", "Window, outside surfaces", 2, new String[]{"b_ext", "b_winbox_out"});
    box(model, "b_gbox", "Glass outer face", 2, "xg0-tol", "xg0+W_g+tol", "yg0-tol", "yg0+H_g+tol", "zg0+t_igu-tol", "zg0+t_igu+tol");
    sint(model, "b_glass_out", "Glazing, outer face", 2, new String[]{"b_ext", "b_gbox"});
    box(model, "b_fsbox", "Frame outer plane (incl. stop lip)", 2, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "zf0+d_f-tol", "zf0+d_f+tol");
    box(model, "b_sabox", "Sash outer plane (behind the stop lip)", 2, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "zsr0+d_f-tol", "zsr0+d_f+tol");
    suni(model, "b_fsunbox", "Frame and sash outer planes", 2, new String[]{"b_fsbox", "b_sabox"});
    sint(model, "b_frame_sun", "Frame and sash, sunlit faces", 2, new String[]{"b_ext", "b_fsunbox"});
    sdiff(model, "b_facade", "Opaque facade", 2, new String[]{"b_out"}, new String[]{"b_win_out"});
    box(model, "b_floor", "Floor slab bottom", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "-t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_ceil", "Ceiling slab top", 2, "-t_w-tol", "W_in+t_w+tol", "H_in+t_slab-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_ceil_in", "Ceiling slab top above the room (span)", 2, "-tol", "W_in+tol", "H_in+t_slab-tol", "H_in+t_slab+tol", "-(t_w+L_in)-tol", "-t_w+tol");
    sdiff(model, "b_ceil_wall", "Ceiling slab top above the walls", 2, new String[]{"b_ceil"}, new String[]{"b_ceil_in"});
    // walls of the storey above: their tops (cut at mid-height) and the faces of the room above
    box(model, "b_uptop_f", "Walls above: top, facade wall", 2, "-t_w-tol", "W_in+t_w+tol", "H_in+t_slab+h_up-tol", "H_in+t_slab+h_up+tol", "-t_w-tol", "tol");
    box(model, "b_uptop_b", "Walls above: top, back wall", 2, "-t_w-tol", "W_in+t_w+tol", "H_in+t_slab+h_up-tol", "H_in+t_slab+h_up+tol", "-(L_in+2*t_w)-tol", "-(L_in+t_w)+tol");
    box(model, "b_uptop_l", "Walls above: top, left wall", 2, "-t_w-tol", "tol", "H_in+t_slab+h_up-tol", "H_in+t_slab+h_up+tol", "-(L_in+t_w)-tol", "-t_w+tol");
    box(model, "b_uptop_r", "Walls above: top, right wall", 2, "W_in-tol", "W_in+t_w+tol", "H_in+t_slab+h_up-tol", "H_in+t_slab+h_up+tol", "-(L_in+t_w)-tol", "-t_w+tol");
    suni(model, "b_uptop_fb", "Walls above: tops of facade and back walls", 2, new String[]{"b_uptop_f", "b_uptop_b"});
    suni(model, "b_uptop_lr", "Walls above: tops of left and right walls", 2, new String[]{"b_uptop_l", "b_uptop_r"});
    suni(model, "b_uptop", "Walls above: all tops", 2, new String[]{"b_uptop_fb", "b_uptop_lr"});
    box(model, "b_upbox", "Room above region", 2, "-tol", "W_in+tol", "H_in+t_slab-tol", "H_in+t_slab+h_up+tol", "-(t_w+L_in)-tol", "-t_w+tol");
    sint(model, "b_room_up", "Faces of the room above (slab span top and inner faces of the walls above)", 2, new String[]{"b_ext", "b_upbox"});
    box(model, "b_left", "Left wall, outer face", 2, "-t_w-tol", "-t_w+tol", "-t_slab-tol", "H_in+t_slab+h_up+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_right", "Right wall, outer face", 2, "W_in+t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+h_up+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_back", "Back wall, outer face", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+h_up+tol", "-(L_in+2*t_w)-tol", "-(L_in+2*t_w)+tol");

    // evaluation points on the room-side faces: frame at z = zf0, sash at z = zsr0 (corner vertices)
    String[][] pts = {
      {"fo_BL", "xf0", "yf0"}, {"fo_BR", "xf0+W_f", "yf0"}, {"fo_TR", "xf0+W_f", "yf0+H_f"}, {"fo_TL", "xf0", "yf0+H_f"},
      {"fi_BL", "xfi0", "yfi0"}, {"fi_BR", "xfi0+W_fi", "yfi0"}, {"fi_TR", "xfi0+W_fi", "yfi0+H_fi"}, {"fi_TL", "xfi0", "yfi0+H_fi"},
      {"sa_BL", "xsa0", "ysa0"}, {"sa_BR", "xsa0+W_sa", "ysa0"}, {"sa_TR", "xsa0+W_sa", "ysa0+H_sa"}, {"sa_TL", "xsa0", "ysa0+H_sa"}
    };
    for (String[] p : pts) pt(model, "p_" + p[0], p[1], p[2], p[0].startsWith("sa_") ? "zsr0" : "zf0");

    // ===================================================================== materials (later ones override)
    mat(model, "m_wall", "Masonry with insulation (U 0.8)", null, "k_wall", "rho_mas", "1000[J/(kg*K)]", "6e-6[1/K]", "E_mas", "0.2");
    mat(model, "m_conc", "Concrete C25/30: slabs and lintel", "s_concrete", "k_con", "rho_con", "1000[J/(kg*K)]", "10e-6[1/K]", "E_con", "0.2");
    if (ADD_SHUTTER_BOX)
      mat(model, "m_sbox", "Roller-shutter box (hollow, insulated)", "s_sbox", "0.08[W/(m*K)]", "100[kg/m^3]", "1400[J/(kg*K)]", "60e-6[1/K]", "0.1[GPa]", "0.3");
    mat(model, "m_joint", "PU foam joint (new stiffness)", "s_joint", "0.035[W/(m*K)]", "30[kg/m^3]", "1400[J/(kg*K)]", "60e-6[1/K]", "E0_jt", "0.3");
    mat(model, "m_frame", "Fixed frame Al/PA, homogenised", "s_frame", "k_frame", "rho_fr", "900[J/(kg*K)]", "23e-6[1/K]", "E_fr", "0.3");
    mat(model, "m_sash", "Sash Al/PA, homogenised", "s_sash", "k_frame", "rho_sa", "900[J/(kg*K)]", "23e-6[1/K]", "E_sa", "0.3");
    mat(model, "m_gk", "Centre gasket EPDM (heat transfer only)", "s_gasket", "0.25[W/(m*K)]", "1150[kg/m^3]", "2000[J/(kg*K)]", "160e-6[1/K]", "E0_gk", "0.45");
    mat(model, "m_sg", "Stop gasket EPDM (soft layer, new stiffness)", "s_gk_stop", "0.25[W/(m*K)]", "1150[kg/m^3]", "2000[J/(kg*K)]", "160e-6[1/K]", "E0_sg", "0");
    mat(model, "m_hinge", "Hinges with bushings (new stiffness)", "s_hinge", "15[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "E0_hw", "0.3");
    mat(model, "m_lock", "Locking points (new stiffness)", "s_lock", "15[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "E0_lk", "0.3");
    mat(model, "m_tb", "Tilt bearing / sash lifter (new stiffness)", "s_tb", "15[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "E0_tb", "0.3");
    mat(model, "m_bed", "Bedding EPDM (new stiffness)", "s_bed", "0.25[W/(m*K)]", "1150[kg/m^3]", "2000[J/(kg*K)]", "160e-6[1/K]", "E0_bed", "0.45");
    mat(model, "m_sbl", "Load-bearing setting blocks (new stiffness)", "s_sbl", "0.2[W/(m*K)]", "950[kg/m^3]", "1800[J/(kg*K)]", "100e-6[1/K]", "E0_sbl", "0.3");
    mat(model, "m_sbd", "Distance blocks (new stiffness)", "s_sbd", "0.2[W/(m*K)]", "950[kg/m^3]", "1800[J/(kg*K)]", "100e-6[1/K]", "E0_sbd", "0.3");
    mat(model, "m_igu", "Glazing 4-14-4, homogenised", "s_igu", "k_igu", "rho_igu", "750[J/(kg*K)]", "9e-6[1/K]", "E_igu", "0.22");
    mat(model, "m_heat", "Heater steel", "s_heater", "45[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "200[GPa]", "0.3");
    mat(model, "m_gap", "Air gap (effective)", "s_gap", "h_gap*g_h", "1.2[kg/m^3]", "1000[J/(kg*K)]", "0[1/K]", "1[kPa]", "0.3");
    mat(model, "m_handle", "Window handle (decorative: light, soft, same expansion as the sash)", "s_handle", "0.2[W/(m*K)]", "1200[kg/m^3]", "1500[J/(kg*K)]", "23e-6[1/K]", "1[GPa]", "0.35");

    // ===================================================================== climate, heater and load ramp as functions of time
    model.component(C).variable().create("var_t");
    model.component(C).variable("var_t").label("Climate, heater and start-up ramp in time");
    model.component(C).variable("var_t").set("t_age", "if(f_season>0.5, t/1[a], t_chk)", "Age in years: time in the aging study; t_chk in the stationary studies (0 on the design day)");
    model.component(C).variable("var_t").set("T_out_v", "if(f_season>0.5, T_mean-A_seas*cos(2*pi*(t_age+t_inst)), T_design)", "Outdoor temperature (monthly mean curve, or design day)");
    model.component(C).variable("var_t").set("P_dem_v", "P_heat*max(0,(T_in-T_out_v)/(T_in-T_design))", "Heater power at the current outdoor temperature");
    model.component(C).variable("var_t").set("Q_heat_v", "P_dem_v/(L_h*H_h*D_h)", "Heater volumetric source");
    model.component(C).variable("var_t").set("f_live_v", "if(f_season>0.5, psi2_live*f_live, f_live)", "Live load acting: quasi-permanent share in the aging study, full on the design day");
    model.component(C).variable("var_t").set("f_ramp", "if(f_season>0.5, min(1, t_age*1[a]/t_ramp), 1)", "Start-up ramp of all loads (aging study)");
    model.component(C).variable("var_t").set("I_warm", "if(t_age>t0_ref, (t_age-t0_ref)/2-(sin(2*pi*(t_age+t_inst))-sin(2*pi*(t0_ref+t_inst)))/(4*pi), 0)", "Warm-season weight (0 at midwinter, 1 at midsummer) integrated since installation, in years");
    model.component(C).variable("var_t").set("N_turn", "f_use*n_turn*365.25*max(0, t_age-t0_ref)", "Full openings (turn) since installation");
    model.component(C).variable("var_t").set("N_tilt", "f_use*n_tilt_s*365.25*I_warm", "Tilt openings since installation (warm months)");
    model.component(C).variable("var_t").set("N_cyc", "N_turn+N_tilt", "Openings and closings since installation (turn + tilt)");
    model.component(C).variable("var_t").set("D_turn", "f_use*n_turn*365.25*(t_turn_w*max(0, t_age-t0_ref)+(t_turn_s-t_turn_w)*I_warm)/1[d]", "Days the window stood fully open since installation");
    model.component(C).variable("var_t").set("D_tilt", "N_tilt*h_tilt/1[d]", "Days the window stood tilted since installation");
    model.component(C).variable("var_t").set("D_closed", "365.25*max(0, t_age-t0_ref)-D_turn-D_tilt", "Days the window was closed since installation");
    model.component(C).variable("var_t").set("s_cb", "f_set*(N_turn+w_cb_tilt*N_tilt)/N_ref", "Corner bearing wear progress: turns count fully, tilts with w_cb_tilt (Archard)");
    model.component(C).variable("var_t").set("s_ss", "f_set*(N_tilt+w_ss_turn*N_turn)/N_ref", "Scissor stay wear progress: tilts count fully, turns with w_ss_turn (Archard)");
    model.component(C).variable("var_t").set("s_wear", "s_cb", "Hinge wear progress of the corner bearing (kept for older plots)");
    model.component(C).variable("var_t").set("s_set", "f_set*(1-exp(-max(0, t_age-t0_ref)/tau_set))", "Setting blocks: permanent set progress (0 at installation, 1 in the long run)");
    model.component(C).variable("var_t").set("d_cb_set", "dv_cb_inf*s_cb", "Corner bearing sinking now");
    model.component(C).variable("var_t").set("d_ss_set", "dh_ss_inf*s_ss", "Scissor stay play now");
    model.component(C).variable("var_t").set("d_sb_set", "d_sb_inf*s_set", "Setting block permanent set now");

    // ===================================================================== soft parts: data for the viscoelastic (memory) model
    suni(model, "s_soft", "Soft parts (viscoelastic)", 3, new String[]{"s_gk_stop", "s_bed", "s_joint", "s_hinge", "s_lock", "s_tb", "s_sbl", "s_sbd"});
    suni(model, "s_visco", "Viscoelastic domains: soft parts (relaxation) and masonry, concrete (creep)", 3, new String[]{"s_soft", "s_masonry", "s_concrete"});
    // last column: 0 = relaxing soft part (tau = relaxation time, bulk from E_inf as before),
    //              1 = creeping masonry/concrete (tau = creep retardation time, bulk modulus stays instantaneous)
    String[][] soft = {{"vs_gk", "s_gk_stop", "Stop gasket", "E0_sg", "r_sg_eff", "tau_epdm", "0", "0"},
                       {"vs_bed", "s_bed", "EPDM bedding", "E0_bed", "r_epdm", "tau_epdm", "0.45", "0"},
                       {"vs_jt", "s_joint", "PU-foam joint", "E0_jt", "r_foam", "tau_foam", "0.3", "0"},
                       {"vs_cb", "s_cb_bot", "Corner bearing, bottom block", "E0_hw", "r_cb_eff", "tau_hw", "0.3", "0"},
                       {"vs_hs", "s_hinge_rest", "Hinge side block and scissor", "E0_hw", "r_hs_eff", "tau_hw", "0.3", "0"},
                       {"vs_lk", "s_lock", "Locking points", "E0_lk", "r_lk_eff", "tau_hw", "0.3", "0"},
                       {"vs_tb", "s_tb", "Tilt bearing / sash lifter", "E0_tb", "r_tb_eff", "tau_hw", "0.3", "0"},
                       {"vs_sbl", "s_sbl", "Load-bearing setting blocks", "E0_sbl", "r_sb", "tau_sb", "0.3", "0"},
                       {"vs_sbd", "s_sbd", "Distance blocks", "E0_sbd", "r_sb", "tau_sb", "0.3", "0"},
                       {"vs_mas", "s_masonry", "Masonry (creep)", "E_mas", "r_mas", "tauc_cr", "0.2", "1"},
                       {"vs_con", "s_concrete", "Concrete (creep)", "E_con", "r_con", "tauc_cr", "0.2", "1"}};
    for (String[] g : soft) {
      model.component(C).variable().create(g[0]);
      model.component(C).variable(g[0]).label((g[7].equals("1") ? "Creep: " : "Soft part: ") + g[2]);
      model.component(C).variable(g[0]).selection().geom("geom1", 3);
      model.component(C).variable(g[0]).selection().named(g[1]);
      model.component(C).variable(g[0]).set("E0_loc", g[3], "Stiffness right after loading");
      model.component(C).variable(g[0]).set("r_loc", g[4], "Long-term / initial stiffness");
      model.component(C).variable(g[0]).set("tau_loc", g[5] + "*1[a]", g[7].equals("1") ? "Creep retardation time" : "Relaxation time of the branch");
      model.component(C).variable(g[0]).set("nu_loc", g[6], "Poisson ratio right after loading");
      model.component(C).variable(g[0]).set("b_loc", g[7], "1: bulk modulus stays instantaneous (creep), 0: from E_inf (soft parts)");
    }
    model.component(C).variable().create("vs_all");
    model.component(C).variable("vs_all").label("Viscoelastic branch: soft parts (relaxation), masonry and concrete (creep)");
    model.component(C).variable("vs_all").selection().geom("geom1", 3);
    model.component(C).variable("vs_all").selection().named("s_visco");
    model.component(C).variable("vs_all").set("E_inf_loc", "r_loc*E0_loc", "Long-term Young's modulus under a lasting load (Linear Elastic Material 2)");
    model.component(C).variable("vs_all").set("K_inf_loc", "if(b_loc>0.5, E0_loc, E_inf_loc)/(3*(1-2*nu_loc))", "Bulk modulus (elastic in the viscoelastic model)");
    model.component(C).variable("vs_all").set("G_inf_loc", "3*K_inf_loc*E_inf_loc/(9*K_inf_loc-E_inf_loc)", "Long-term shear modulus");
    model.component(C).variable("vs_all").set("nu_inf_loc", "(3*K_inf_loc-2*G_inf_loc)/(2*(3*K_inf_loc+G_inf_loc))", "Long-term Poisson ratio (Linear Elastic Material 2)");
    model.component(C).variable("vs_all").set("Gv_loc", "3*K_inf_loc*E0_loc/(9*K_inf_loc-E0_loc)-G_inf_loc", "Branch shear modulus: instantaneous Young's modulus = E0");
    model.component(C).variable("vs_all").set("tauv_loc", "if(b_loc>0.5, tau_loc*G_inf_loc/(G_inf_loc+Gv_loc), tau_loc)", "Relaxation time of the branch (from the creep retardation time for masonry, concrete)");

    // ===================================================================== heat transfer
    model.component(C).physics().create("ht", "HeatTransfer", "geom1");
    model.component(C).physics("ht").feature("init1").set("Tinit", "T_in");
    model.component(C).physics("ht").create("hs1", "HeatSource", 3);
    model.component(C).physics("ht").feature("hs1").label("Heater power (scaled with outdoor temperature)");
    model.component(C).physics("ht").feature("hs1").selection().named("s_heater");
    model.component(C).physics("ht").feature("hs1").set("Q0", "Q_heat_v");
    hflux(model, "hf_in", "Indoor air", "b_in", "h_in", "T_in");
    hflux(model, "hf_win_in", "Indoor air at the window", "b_win_in", "h_in", "T_in+dT_plume");
    hflux(model, "hf_heater", "Heater to room", "b_heat", "h_heater", "T_in");
    hflux(model, "hf_out", "Outdoor air", "b_out", "h_out", "T_out_v");
    String[][] nb = {{"left", "b_left", "e_left"}, {"right", "b_right", "e_right"}, {"back", "b_back", "e_back"},
                     {"up", ADD_UPPER_WALL ? "b_room_up" : "b_ceil", "e_up"}, {"down", "b_floor", "e_down"}};
    for (String[] n : nb)
      hflux(model, "hf_" + n[0], "Neighbour or outdoor: " + n[0], n[1], n[2] + "*h_out+(1-" + n[2] + ")*h_in", n[2] + "*T_out_v+(1-" + n[2] + ")*T_adj");
    qflux(model, "sun_facade", "Sun on facade", "b_facade", "a_wall*G_t");
    qflux(model, "sun_frame", "Sun on frame and sash", "b_frame_sun", "a_frame*G_t");
    qflux(model, "sun_glass", "Sun absorbed in glazing", "b_glass_out", "a_igu*G_t");

    // ===================================================================== solid mechanics
    model.component(C).physics().create("solid", "SolidMechanics", "geom1");
    model.component(C).physics("solid").selection().named("s_mech");
    try {
      model.component(C).physics("solid").prop("StructuralTransientBehavior").set("StructuralTransientBehavior", "Quasistatic");
    } catch (Exception e) {
      System.out.println("NOTE: in Solid Mechanics > Structural Transient Behavior choose Quasistatic. " + e.getMessage());
    }
    model.component(C).physics("solid").create("roll1", "Roller", 2);
    model.component(C).physics("solid").feature("roll1").label("Floor slab rests on the storey below");
    model.component(C).physics("solid").feature("roll1").selection().named("b_floor");
    boolean held = false;
    if (ADD_UPPER_WALL) {
      // the walls above are held at their mid-height across their own plane (the building holds them there); along the wall and upward they are free
      held = presDisp(model, "disp_up_fb", "Walls above held at mid-height: facade and back walls (z)", "b_uptop_fb", 2)
          && presDisp(model, "disp_up_lr", "Walls above held at mid-height: left and right walls (x)", "b_uptop_lr", 0);
    }
    if (!held) {
      model.component(C).physics("solid").create("rms1", "RigidMotionSuppression", 3);
      model.component(C).physics("solid").feature("rms1").selection().named("s_concrete");
    }
    model.component(C).physics("solid").create("bl1", "BodyLoad", 3);
    model.component(C).physics("solid").feature("bl1").label("Building self-weight (f_dead)");
    model.component(C).physics("solid").feature("bl1").selection().named("s_bldg");
    model.component(C).physics("solid").feature("bl1").set("FperVol", new String[]{"0", "-f_ramp*f_dead*solid.rho*g_const", "0"});
    model.component(C).physics("solid").create("bl2", "BodyLoad", 3);
    model.component(C).physics("solid").feature("bl2").label("Window self-weight (always on)");
    model.component(C).physics("solid").feature("bl2").selection().named("s_win_mech");
    model.component(C).physics("solid").feature("bl2").set("FperVol", new String[]{"0", "-f_ramp*solid.rho*g_const", "0"});
    // tilted sash: gravity turned by the tilt angle relative to the sash (correction added to bl2; zero unless st_tilt = 1)
    model.component(C).physics("solid").create("bl_tilt", "BodyLoad", 3);
    model.component(C).physics("solid").feature("bl_tilt").label("Sash tilted: gravity turned by the tilt angle (st_tilt)");
    model.component(C).physics("solid").feature("bl_tilt").selection().named("s_sashgrp");
    model.component(C).physics("solid").feature("bl_tilt").set("FperVol", new String[]{"0", "f_ramp*solid.rho*g_const*(1-cos(st_tilt*th_tilt))", "-f_ramp*solid.rho*g_const*sin(st_tilt*th_tilt)"});
    if (ADD_UPPER_WALL)
      bload(model, "bnl1", "Storeys above: dead load, on top of the walls above (f_dead)", "b_uptop", new String[]{"0", "-f_ramp*f_dead*p_roof_top", "0"});
    else
      bload(model, "bnl1", "Storeys above: dead load, down through the walls (f_dead)", "b_ceil_wall", new String[]{"0", "-f_ramp*f_dead*p_roof_w", "0"});
    bload(model, "bnl3", "Floor above: live load on the slab span (f_live)", "b_ceil_in", new String[]{"0", "-f_ramp*f_live_v*p_live", "0"});
    bload(model, "bnl2", "Wind on the window (f_wind)", "b_win_out", new String[]{"0", "0", "-f_ramp*f_wind*p_wind"});

    // soft parts: long-term stiffness in Linear Elastic Material 2, memory in its Viscoelasticity sub-node
    model.component(C).physics("solid").create("lemm2", "LinearElasticModel", 3);
    model.component(C).physics("solid").feature("lemm2").label("Linear Elastic Material 2: soft parts, long-term stiffness + memory");
    model.component(C).physics("solid").feature("lemm2").selection().named("s_visco");
    try {
      model.component(C).physics("solid").feature("lemm2").set("E_mat", "userdef");
      model.component(C).physics("solid").feature("lemm2").set("E", "E_inf_loc");
      model.component(C).physics("solid").feature("lemm2").set("nu_mat", "userdef");
      model.component(C).physics("solid").feature("lemm2").set("nu", "nu_inf_loc");
    } catch (Exception e) {
      System.out.println("NOTE: in Linear Elastic Material 2 set Young's modulus = E_inf_loc and Poisson's ratio = nu_inf_loc (User defined). " + e.getMessage());
    }
    try {
      model.component(C).physics("solid").feature("lemm2").create("vis1", "Viscoelasticity", 3);
      model.component(C).physics("solid").feature("lemm2").feature("vis1").label("Viscoelasticity 1: one relaxing branch (memory)");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("MaterialModel", "SLS");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("shiftFunction", "none");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("Gv", "Gv_loc");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("tauv", "tauv_loc");
    } catch (Exception e) {
      try { model.component(C).physics("solid").feature("lemm2").feature("vis1").active(false); } catch (Exception e2) { }
      System.out.println("NOTE: Viscoelasticity 1 is switched OFF because it could not be set from Java. "
        + "Set Material model SLS, shear modulus Gv_loc, relaxation time tauv_loc, then enable it. " + e.getMessage());
    }

    // hinge settlement: built-in strains growing with s_set(t), zero at installation
    String[][] sets = {{"set_cb", "s_cb_bot", "Hinge wear: corner bearing sinks (cycles)", "0", "-d_cb_set/s_gap"},
                       {"set_ss", "s_ss_top", "Hinge wear: scissor stay play (cycles)", "d_ss_set/s_gap", "0"},
                       {"set_sb", "s_sbl", "Setting blocks take a permanent set (time)", "0", "-d_sb_set/g_e"}};
    for (String[] st : sets) {
      try {
        model.component(C).physics("solid").feature("lemm2").create(st[0], "InitialStressandStrain", 3);
        model.component(C).physics("solid").feature("lemm2").feature(st[0]).label(st[2]);
        model.component(C).physics("solid").feature("lemm2").feature(st[0]).selection().named(st[1]);
        model.component(C).physics("solid").feature("lemm2").feature(st[0]).set("eil", new String[]{st[3], "0", "0", "0", st[4], "0", "0", "0", "0"});
      } catch (Exception e) {
        System.out.println("NOTE: add Linear Elastic Material 2 > Initial Stress and Strain '" + st[2] + "' on selection " + st[1]
          + " with initial strain xx = " + st[3] + ", yy = " + st[4] + " (all other components 0). " + e.getMessage());
      }
    }
    // closing pressure: the locks pull the sash onto the stop gasket, which wants to be c0_gk thicker than its gap.
    // One node under Linear Elastic Material 2 (aging study, relaxes with the gasket), one under Material 1 (design day, Material 2 off).
    String[][] pre = {{"lemm2", "pre_sg2", "Stop gasket closing pressure (aging study)"}, {"lemm1", "pre_sg1", "Stop gasket closing pressure (design day)"}};
    for (String[] pr : pre) {
      try {
        model.component(C).physics("solid").feature(pr[0]).create(pr[1], "InitialStressandStrain", 3);
        model.component(C).physics("solid").feature(pr[0]).feature(pr[1]).label(pr[2]);
        model.component(C).physics("solid").feature(pr[0]).feature(pr[1]).selection().named("s_gk_stop");
        model.component(C).physics("solid").feature(pr[0]).feature(pr[1]).set("eil", new String[]{"0", "0", "0", "0", "0", "0", "0", "0", "f_gkp*f_ramp*c0_gk/t_sg"});
      } catch (Exception e) {
        System.out.println("NOTE: add " + pr[0] + " > Initial Stress and Strain on s_gk_stop with initial strain zz = f_gkp*f_ramp*c0_gk/t_sg. " + e.getMessage());
      }
    }

    // locks: stiff out of plane (press the sash onto the gasket), soft in plane (cams slide in their keepers)
    model.component(C).physics("solid").create("lemm3", "LinearElasticModel", 3);
    model.component(C).physics("solid").feature("lemm3").label("Linear Elastic Material 3: locks, stiff out of plane, soft in plane");
    model.component(C).physics("solid").feature("lemm3").selection().named("s_lock");
    try {
      model.component(C).physics("solid").feature("lemm3").set("SolidModel", "Orthotropic");
      model.component(C).physics("solid").feature("lemm3").set("Evector_mat", "userdef");
      model.component(C).physics("solid").feature("lemm3").set("Evector", new String[]{"E_lk_ip", "E_lk_ip", "E0_lk"});
      model.component(C).physics("solid").feature("lemm3").set("nuvector_mat", "userdef");
      model.component(C).physics("solid").feature("lemm3").set("nuvector", new String[]{"0", "0", "0"});
      model.component(C).physics("solid").feature("lemm3").set("Gvector_mat", "userdef");
      model.component(C).physics("solid").feature("lemm3").set("Gvector", new String[]{"G_lk_ip", "G_lk_z", "G_lk_z"});
    } catch (Exception e) {
      try {
        model.component(C).physics("solid").feature("lemm3").set("SolidModel", "Isotropic");
        model.component(C).physics("solid").feature("lemm3").set("E_mat", "userdef");
        model.component(C).physics("solid").feature("lemm3").set("E", "E_lk_ip");
      } catch (Exception e2) { }
      System.out.println("NOTE: the locks could not be made orthotropic from Java and are soft in every direction (E = E_lk_ip). "
        + "For the intended behaviour set Linear Elastic Material 3 to Orthotropic, User defined: E = {E_lk_ip, E_lk_ip, E0_lk}, "
        + "nu = {0, 0, 0}, G = {G_lk_ip, G_lk_z, G_lk_z}. " + e.getMessage());
    }

    // thermal expansion: multiphysics coupling only
    model.component(C).multiphysics().create("te1", "ThermalExpansion", 3);
    model.component(C).multiphysics("te1").selection().named("s_mech");
    try {
      model.component(C).multiphysics("te1").set("Tref", "T_ref");
    } catch (Exception e) {
      System.out.println("NOTE: reference temperature of Multiphysics > Thermal Expansion stays at its default (20 degC = T_ref).");
    }

    // ===================================================================== output variables
    for (String[] p : pts) {
      model.component(C).cpl().create("ip_" + p[0], "Integration");
      model.component(C).cpl("ip_" + p[0]).label("Value at " + p[0]);
      model.component(C).cpl("ip_" + p[0]).selection().geom("geom1", 0);
      model.component(C).cpl("ip_" + p[0]).selection().named("p_" + p[0]);
    }
    model.component(C).variable().create("var1");
    model.component(C).variable("var1").label("Frame-sash alignment");
    for (String[] p : pts) {
      model.component(C).variable("var1").set("u_" + p[0], "ip_" + p[0] + "(u)", "x displacement at " + p[0]);
      model.component(C).variable("var1").set("v_" + p[0], "ip_" + p[0] + "(v)", "y displacement at " + p[0]);
      model.component(C).variable("var1").set("w_" + p[0], "ip_" + p[0] + "(w)", "z displacement at " + p[0]);
    }
    model.component(C).cpl().create("maxsg", "Maximum");
    model.component(C).cpl("maxsg").label("Maximum over the stop gasket");
    model.component(C).cpl("maxsg").selection().geom("geom1", 3);
    model.component(C).cpl("maxsg").selection().named("s_gk_stop");
    model.component(C).cpl().create("minsg", "Minimum");
    model.component(C).cpl("minsg").label("Minimum over the stop gasket");
    model.component(C).cpl("minsg").selection().geom("geom1", 3);
    model.component(C).cpl("minsg").selection().named("s_gk_stop");
    String[][] V = {
      // frame skew (outer corners)
      {"skew_frame", "((u_fo_TR-u_fo_BL)*W_f+(v_fo_TR-v_fo_BL)*H_f-(u_fo_TL-u_fo_BR)*(-W_f)-(v_fo_TL-v_fo_BR)*H_f)/sqrt(W_f^2+H_f^2)", "Frame skew (diagonal 1 minus diagonal 2)"},
      // sash skew
      {"skew_sash", "((u_sa_TR-u_sa_BL)*W_sa+(v_sa_TR-v_sa_BL)*H_sa-(u_sa_TL-u_sa_BR)*(-W_sa)-(v_sa_TL-v_sa_BR)*H_sa)/sqrt(W_sa^2+H_sa^2)", "Sash skew"},
      // rebate gap changes at the corners (positive = gap opens)
      {"g_top_L", "v_fi_TL-v_sa_TL", "Top gap change, hinge end"},
      {"g_top_R", "v_fi_TR-v_sa_TR", "Top gap change, lock end"},
      {"g_bot_L", "v_sa_BL-v_fi_BL", "Bottom gap change, hinge end"},
      {"g_bot_R", "v_sa_BR-v_fi_BR", "Bottom gap change, lock end"},
      {"g_hin_B", "u_sa_BL-u_fi_BL", "Hinge-side gap change, bottom"},
      {"g_hin_T", "u_sa_TL-u_fi_TL", "Hinge-side gap change, top"},
      {"g_lck_B", "u_fi_BR-u_sa_BR", "Lock-side gap change, bottom"},
      {"g_lck_T", "u_fi_TR-u_sa_TR", "Lock-side gap change, top"},
      {"sag_lock", "v_sa_BR-v_fi_BR", "Sash drop at the lock-side bottom corner relative to the frame"},
      {"g_min", "min(min(min(g_top_L,g_top_R),min(g_bot_L,g_bot_R)),min(min(g_hin_B,g_hin_T),min(g_lck_B,g_lck_T)))", "Largest gap closure"},
      {"rub_margin", "s_gap+g_min", "Remaining rebate gap (below 0: sash rubs on frame)"},
      // out-of-plane mismatch (gasket compression changes)
      {"dz_BL", "w_sa_BL-w_fi_BL", "Out-of-plane offset sash-frame, BL"},
      {"dz_BR", "w_sa_BR-w_fi_BR", "Out-of-plane offset sash-frame, BR"},
      {"dz_TR", "w_sa_TR-w_fi_TR", "Out-of-plane offset sash-frame, TR"},
      {"dz_TL", "w_sa_TL-w_fi_TL", "Out-of-plane offset sash-frame, TL"},
      {"dz_max", "max(max(abs(dz_BL),abs(dz_BR)),max(abs(dz_TR),abs(dz_TL)))", "Largest gasket offset (leak risk)"},
      {"twist_rel", "(dz_BL+dz_TR)-(dz_BR+dz_TL)", "Twist of the sash relative to the frame"},
      // sealing: sash and frame moving apart at the stop gasket (positive = the gasket opens)
      {"gk_open", "maxsg(solid.eZZ)*t_sg", "Largest opening of the stop gasket: sash moved away from the frame stop"},
      {"gk_press", "-minsg(solid.eZZ)*t_sg", "Largest extra compression of the stop gasket"},
      {"seal_margin", "c0_gk-gk_open", "Gasket compression left where it opens most (below 0: the seal has lost contact)"}
    };
    for (String[] v : V) model.component(C).variable("var1").set(v[0], v[1], v[2]);
    box(model, "b_lin_bot", "Lintel underside (top of the opening)", 2, "xo0-tol", "xo0+W_o+tol", "yo0+H_o-tol", "yo0+H_o+tol", "-t_w-tol", "tol");
    String[][] avs = {{"av_frame", "s_frame", "3", "Average over the fixed frame"}, {"av_sash", "s_sash", "3", "Average over the sash"},
                      {"av_igu", "s_igu", "3", "Average over the glazing"}, {"av_lin", "b_lin_bot", "2", "Average over the lintel underside"},
                      {"av_cb", "s_cb_bot", "3", "Average over the corner bearing, bottom block"}, {"av_cbs", "s_cb_side", "3", "Average over the corner bearing, side block"},
                      {"av_ss", "s_ss_top", "3", "Average over the scissor stay block"}, {"av_tb", "s_tb", "3", "Average over the tilt bearing / sash lifter"}};
    for (String[] a : avs) {
      model.component(C).cpl().create(a[0], "Average");
      model.component(C).cpl(a[0]).label(a[3]);
      model.component(C).cpl(a[0]).selection().geom("geom1", Integer.parseInt(a[2]));
      model.component(C).cpl(a[0]).selection().named(a[1]);
    }
    model.component(C).variable().create("var2");
    model.component(C).variable("var2").label("Checks: temperatures and lintel");
    model.component(C).variable("var2").set("T_frame", "av_frame(T)", "Mean temperature of the fixed frame");
    model.component(C).variable("var2").set("T_sash", "av_sash(T)", "Mean temperature of the sash");
    model.component(C).variable("var2").set("T_glass", "av_igu(T)", "Mean temperature of the glazing");
    model.component(C).variable("var2").set("v_lintel", "av_lin(v)", "Mean vertical displacement of the lintel underside");
    model.component(C).variable().create("var3");
    model.component(C).variable("var3").label("Hinge forces (mean stress x block face)");
    model.component(C).variable("var3").set("F_cb_v", "-av_cb(solid.sy)*L_hw*d_hw", "Corner bearing: vertical force (carries the sash)");
    model.component(C).variable("var3").set("F_cb_h", "-av_cbs(solid.sx)*L_hw*d_hw", "Corner bearing: horizontal push at the bottom (positive towards the jamb)");
    model.component(C).variable("var3").set("F_ss_h", "av_ss(solid.sx)*L_hw*d_hw", "Scissor stay: horizontal pull at the top (positive: holds the sash top at the jamb)");
    model.component(C).variable("var3").set("F_ss_z", "av_ss(solid.sxz)*L_hw*d_hw", "Scissor stay: force across the window plane (holds the tilted sash)");
    model.component(C).variable("var3").set("F_tb_v", "-av_tb(solid.sy)*L_hw*d_hw", "Tilt bearing / sash lifter: vertical force");

    // ===================================================================== mesh
    model.component(C).mesh("mesh1").feature("size").set("hauto", 5);
    model.component(C).mesh("mesh1").create("ftet1", "FreeTet");
    model.component(C).mesh("mesh1").feature("ftet1").create("size1", "Size");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").label("Window region");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").selection().geom("geom1", 3);
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").selection().named("s_meshwin");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").set("custom", "on");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").set("hmax", "0.025[m]");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").set("hmaxactive", true);
    try {
      model.component(C).mesh("mesh1").run();
    } catch (Exception e) {
      System.out.println("WARNING: mesh did not build; open Mesh 1 > Build All to see why. " + e.getMessage());
    }

    // ===================================================================== studies
    model.study().create("std1");
    model.study("std1").label("1 Aging with memory: t = 0 to t_end years (one click)");
    model.study("std1").create("time", "Transient");
    model.study("std1").feature("time").set("tunit", "a");
    model.study("std1").feature("time").set("tlist", "0 0.002 0.01 0.05 range(0.25,dt_out,t_end)");
    model.study("std1").feature("time").setSolveFor("/physics/ht", true);
    model.study("std1").feature("time").setSolveFor("/physics/solid", true);
    try {
      model.study("std1").feature("time").set("rtolactive", true);
      model.study("std1").feature("time").set("rtol", "0.001");
    } catch (Exception e) {
      System.out.println("NOTE: optional: set the relative tolerance of Study 1 to 0.001.");
    }

    model.study().create("std2");
    model.study("std2").label("2 Design winter day check: -5 degC, new materials");
    model.study("std2").create("stat", "Stationary");
    model.study("std2").feature("stat").setSolveFor("/physics/ht", true);
    model.study("std2").feature("stat").setSolveFor("/physics/solid", true);
    model.study("std2").feature("stat").set("useparam", true);
    model.study("std2").feature("stat").set("sweeptype", "filled");
    model.study("std2").feature("stat").setIndex("pname", "f_season", 0);
    model.study("std2").feature("stat").setIndex("plistarr", "0", 0);
    model.study("std2").feature("stat").setIndex("punit", "", 0);
    model.study("std2").feature("stat").setIndex("pname", "f_dead", 1);
    model.study("std2").feature("stat").setIndex("plistarr", "0", 1);
    model.study("std2").feature("stat").setIndex("punit", "", 1);
    try {
      model.study("std2").feature("stat").set("useadvanceddisable", true);
      model.study("std2").feature("stat").set("disabledphysics", new String[]{"solid/lemm2"});
    } catch (Exception e) {
      System.out.println("NOTE: in Study 2 > Step 1 tick 'Modify model configuration for study step' and disable Linear Elastic Material 2.");
    }
    // Studies 3 and 4: the sash open. Mechanics only, at 20 deg C (temperature stays at its initial value T_in = T_ref,
    // so there is no thermal strain); window weight only; locks and stop gasket disengaged; new window. They check the load ratios behind phi.
    String[][] openStates = {{"std3", "3 Sash tilted: load-path check, new window", "1", "1"},
                             {"std4", "4 Sash turned open: load-path check, new window", "1e-4", "0"}};
    for (String[] os : openStates) {
      model.study().create(os[0]);
      model.study(os[0]).label(os[1]);
      model.study(os[0]).create("stat", "Stationary");
      model.study(os[0]).feature("stat").setSolveFor("/physics/ht", false);
      model.study(os[0]).feature("stat").setSolveFor("/physics/solid", true);
      model.study(os[0]).feature("stat").set("useparam", true);
      model.study(os[0]).feature("stat").set("sweeptype", "filled");
      String[][] sw = {{"f_season", "0"}, {"f_dead", "0"}, {"f_live", "0"}, {"f_gkp", "0"}, {"f_lock", "1e-4"}, {"f_stopg", "1e-3"},
                       {"f_tb", os[2]}, {"st_tilt", os[3]}, {"t_chk", "0"}};
      for (int k = 0; k < sw.length; k++) {
        model.study(os[0]).feature("stat").setIndex("pname", sw[k][0], k);
        model.study(os[0]).feature("stat").setIndex("plistarr", sw[k][1], k);
        model.study(os[0]).feature("stat").setIndex("punit", "", k);
      }
      try {
        model.study(os[0]).feature("stat").set("useadvanceddisable", true);
        model.study(os[0]).feature("stat").set("disabledphysics", new String[]{"solid/lemm2"});
      } catch (Exception e) {
        System.out.println("NOTE: in " + os[0] + " > Step 1 tick 'Modify model configuration for study step' and disable Linear Elastic Material 2.");
      }
    }
    // Study 5: the same aging as Study 1, but the window is never opened (f_use = 0): Study 1 - Study 5 = effect of use
    model.study().create("std5");
    model.study("std5").label("5 Aging, window never opened (comparison: effect of use)");
    model.study("std5").create("time", "Transient");
    model.study("std5").feature("time").set("tunit", "a");
    model.study("std5").feature("time").set("tlist", "0 0.002 0.01 0.05 range(0.25,dt_out,t_end)");
    model.study("std5").feature("time").setSolveFor("/physics/ht", true);
    model.study("std5").feature("time").setSolveFor("/physics/solid", true);
    try {
      model.study("std5").feature("time").set("useparam", true);
      model.study("std5").feature("time").setIndex("pname", "f_use", 0);
      model.study("std5").feature("time").setIndex("plistarr", "0", 0);
      model.study("std5").feature("time").setIndex("punit", "", 0);
      model.study("std5").feature("time").set("rtolactive", true);
      model.study("std5").feature("time").set("rtol", "0.001");
    } catch (Exception e) {
      System.out.println("NOTE: in Study 5 > Time Dependent > Study Extensions tick Auxiliary sweep with f_use = 0 (or set f_use = 0 in Parameters and run Study 1).");
    }
    for (String st : new String[]{"std1", "std2", "std3", "std4", "std5"}) {
      try { model.study(st).createAutoSequences("all"); } catch (Exception e) {
        System.out.println("NOTE: solver for " + st + " will be generated on Compute.");
      }
    }
    try {
      model.sol("sol5").feature("t1").set("tstepsbdf", "intermediate");
    } catch (Exception e) {
      System.out.println("NOTE: optional: in Solution 5 > Time-Dependent Solver 1 set Steps taken by solver = Intermediate.");
    }
    try {
      model.sol("sol1").feature("t1").set("tstepsbdf", "intermediate");
    } catch (Exception e) {
      System.out.println("NOTE: optional: in Solution 1 > Time-Dependent Solver 1 set Steps taken by solver = Intermediate.");
    }

    // ===================================================================== results
    String outs = "skew_frame skew_sash sag_lock rub_margin g_min dz_max twist_rel g_top_R g_lck_T g_bot_R g_hin_T gk_open gk_press seal_margin";
    String[] ex = outs.split(" ");
    String[][] checks = {{"d_cb_set", "mm"}, {"d_ss_set", "mm"}, {"d_sb_set", "mm"}, {"N_cyc", "1"}, {"N_turn", "1"}, {"N_tilt", "1"}, {"D_turn", "1"}, {"D_tilt", "1"}, {"D_closed", "1"}, {"v_lintel", "mm"}, {"T_frame", "degC"}, {"T_sash", "degC"}, {"T_glass", "degC"}, {"P_dem_v", "W"}};
    try {
      String[][] runs = {{"dset2", "Design winter day"}, {"dset1", "Aging with memory"}};
      for (int r = 0; r < runs.length; r++) {
        String tag = "gev" + (r + 1);
        model.result().numerical().create(tag, "EvalGlobal");
        model.result().numerical(tag).label("Frame-sash alignment and checks, " + runs[r][1]);
        model.result().numerical(tag).set("data", runs[r][0]);
        int i = 0;
        for (String e : ex) { model.result().numerical(tag).setIndex("expr", e, i); model.result().numerical(tag).setIndex("unit", "mm", i); i++; }
        for (String[] c : checks) { model.result().numerical(tag).setIndex("expr", c[0], i); model.result().numerical(tag).setIndex("unit", c[1], i); i++; }
      }
      model.result().numerical().create("gev3", "EvalGlobal");
      model.result().numerical("gev3").label("Aging: change since installation (t = t0_ref)");
      model.result().numerical("gev3").set("data", "dset1");
      int i = 0;
      for (String e : ex) {
        model.result().numerical("gev3").setIndex("expr", e + "-at(t0_ref*1[a]," + e + ")", i);
        model.result().numerical("gev3").setIndex("unit", "mm", i);
        model.result().numerical("gev3").setIndex("descr", "change of " + e + " since installation", i);
        i++;
      }
      model.result().numerical("gev3").setIndex("expr", "v_lintel-at(t0_ref*1[a],v_lintel)", i);
      model.result().numerical("gev3").setIndex("unit", "mm", i);
      String[][] openTabs = {{"gev4", "dset3", "Load-path check: sash tilted (new window)"}, {"gev5", "dset4", "Load-path check: sash turned open (new window)"}};
      String[][] openEx = {{"sag_lock", "mm"}, {"dz_max", "mm"}, {"twist_rel", "mm"}, {"rub_margin", "mm"}, {"F_cb_v", "N"}, {"F_cb_h", "N"},
                           {"F_ss_h", "N"}, {"F_ss_z", "N"}, {"F_tb_v", "N"}, {"d_cb_set", "mm"}, {"d_ss_set", "mm"}};
      model.result().numerical().create("gev6", "EvalGlobal");
      model.result().numerical("gev6").label("Aging, window never opened: change since installation (compare with the Aging table)");
      model.result().numerical("gev6").set("data", "dset5");
      int j = 0;
      for (String e : ex) {
        model.result().numerical("gev6").setIndex("expr", e + "-at(t0_ref*1[a]," + e + ")", j);
        model.result().numerical("gev6").setIndex("unit", "mm", j);
        model.result().numerical("gev6").setIndex("descr", "change of " + e + " since installation, never opened", j);
        j++;
      }
      for (String[] ot : openTabs) {
        model.result().numerical().create(ot[0], "EvalGlobal");
        model.result().numerical(ot[0]).label(ot[2]);
        model.result().numerical(ot[0]).set("data", ot[1]);
        for (int k = 0; k < openEx.length; k++) {
          model.result().numerical(ot[0]).setIndex("expr", openEx[k][0], k);
          model.result().numerical(ot[0]).setIndex("unit", openEx[k][1], k);
        }
      }
    } catch (Exception e) {
      System.out.println("NOTE: create the Global Evaluation tables by hand (variables in Definitions > Frame-sash alignment).");
    }
    try {
      model.result().create("pg1", "PlotGroup1D");
      model.result("pg1").label("Aging with memory: change since installation over time");
      model.result("pg1").set("data", "dset1");
      model.result("pg1").create("glob1", "Global");
      String[] g1 = {"sag_lock", "rub_margin", "skew_sash", "dz_max"};
      for (int i = 0; i < g1.length; i++) {
        model.result("pg1").feature("glob1").setIndex("expr", g1[i] + "-at(t0_ref*1[a]," + g1[i] + ")", i);
        model.result("pg1").feature("glob1").setIndex("unit", "mm", i);
      }
      model.result().create("pg2", "PlotGroup3D");
      model.result("pg2").label("Temperature, design day");
      model.result("pg2").set("data", "dset2");
      model.result("pg2").create("surf1", "Surface");
      model.result("pg2").feature("surf1").set("expr", "T");
      model.result("pg2").feature("surf1").set("unit", "degC");
      model.result().create("pg3", "PlotGroup3D");
      model.result("pg3").label("Displacement, design day (scaled)");
      model.result("pg3").set("data", "dset2");
      model.result("pg3").create("surf1", "Surface");
      model.result("pg3").feature("surf1").set("expr", "solid.disp");
      model.result("pg3").feature("surf1").set("unit", "mm");
      model.result("pg3").feature("surf1").create("def1", "Deform");
      model.result("pg3").feature("surf1").feature("def1").set("scaleactive", true);
      model.result("pg3").feature("surf1").feature("def1").set("scale", "200");

      String[][] openPlots = {{"pg6", "dset3", "Load-path check: sash tilted, window only (scaled)"}, {"pg7", "dset4", "Load-path check: sash turned open, window only (scaled)"}};
      for (String[] op : openPlots) {
        model.result().create(op[0], "PlotGroup3D");
        model.result(op[0]).label(op[2]);
        model.result(op[0]).set("data", op[1]);
        try {
          model.result(op[0]).selection().geom("geom1", 3);
          model.result(op[0]).selection().named("s_window");
        } catch (Exception e) {
          System.out.println("NOTE: optional: restrict the plot '" + op[2] + "' to the window domains.");
        }
        model.result(op[0]).create("surf1", "Surface");
        model.result(op[0]).feature("surf1").set("expr", "solid.disp");
        model.result(op[0]).feature("surf1").set("unit", "mm");
        model.result(op[0]).feature("surf1").create("def1", "Deform");
        model.result(op[0]).feature("surf1").feature("def1").set("scaleactive", true);
        model.result(op[0]).feature("surf1").feature("def1").set("scale", "200");
      }

      model.result().create("pg5", "PlotGroup1D");
      model.result("pg5").label("Hinge settlement (input) and sash response over time");
      model.result("pg5").set("data", "dset1");
      model.result("pg5").create("glob1", "Global");
      String[] g5 = {"d_cb_set", "d_ss_set", "d_sb_set", "sag_lock-at(t0_ref*1[a],sag_lock)", "rub_margin-at(t0_ref*1[a],rub_margin)"};
      for (int i = 0; i < g5.length; i++) {
        model.result("pg5").feature("glob1").setIndex("expr", g5[i], i);
        model.result("pg5").feature("glob1").setIndex("unit", "mm", i);
      }
    } catch (Exception e) {
      System.out.println("NOTE: plots will be generated on Compute.");
    }
    String dU = "u-at(t0_ref*1[a],u)", dV = "v-at(t0_ref*1[a],v)", dW = "w-at(t0_ref*1[a],w)";
    try {
      model.result().create("pg4", "PlotGroup3D");
      model.result("pg4").label("Aging drift since installation, window only (scaled)");
      model.result("pg4").set("data", "dset1");
      try {
        model.result("pg4").selection().geom("geom1", 3);
        model.result("pg4").selection().named("s_window");
      } catch (Exception e) {
        System.out.println("NOTE: optional: restrict the plot 'Aging drift since installation' to the window domains.");
      }
      model.result("pg4").create("surf1", "Surface");
      model.result("pg4").feature("surf1").set("expr", "sqrt((" + dU + ")^2+(" + dV + ")^2+(" + dW + ")^2)");
      model.result("pg4").feature("surf1").set("unit", "mm");
      model.result("pg4").feature("surf1").set("rangecoloractive", true);
      model.result("pg4").feature("surf1").set("rangecolormin", "0");
      model.result("pg4").feature("surf1").set("rangecolormax", "1.6");
      model.result("pg4").feature("surf1").create("def1", "Deform");
      model.result("pg4").feature("surf1").feature("def1").set("expr", new String[]{dU, dV, dW});
      model.result("pg4").feature("surf1").feature("def1").set("scaleactive", true);
      model.result("pg4").feature("surf1").feature("def1").set("scale", "20");
    } catch (Exception e) {
      System.out.println("NOTE: create the drift plot by hand: Surface sqrt((u-at(t0_ref*1[a],u))^2+...) in mm, Deformation u-at(t0_ref*1[a],u), ..., scale 20.");
    }
    try {
      model.result().export().create("anim1", "Animation");
      model.result().export("anim1").label("Animation: aging drift, winters only");
      model.result().export("anim1").set("target", "player");
      model.result().export("anim1").set("plotgroup", "pg4");
      model.result().export("anim1").set("fps", 1);
      boolean winters = false;
      try {
        model.result().export("anim1").set("looplevelinput", "manualindices");
        model.result().export("anim1").set("looplevelindices", "4 8 12 16 20 24 28 32 36 40 44");
        winters = true;
      } catch (Exception e1) { }
      if (!winters) {
        try {
          model.result().export("anim1").set("looplevelinput", "manual");
          model.result().export("anim1").set("looplevel", "0.05 1 2 3 4 5 6 7 8 9 10");
          winters = true;
        } catch (Exception e2) { }
      }
      if (!winters) {
        try { model.result().export("anim1").set("looplevelinput", "all"); } catch (Exception e3) { }
        System.out.println("NOTE: in Export > Animation: aging drift, winters only, choose the times 0.05 1 2 3 4 5 6 7 8 9 10 by hand.");
      }
    } catch (Exception e) {
      System.out.println("NOTE: create Export > Animation for the plot 'Aging drift since installation' with the times 0.05 1 2 ... 10.");
    }

    return model;
  }

  public static void main(String[] args) {
    run();
  }
}
