/*
 * (COMSOL 6.x with Structural Mechanics Module): memory + hinge settlement
 */

import com.comsol.model.*;
import com.comsol.model.util.*;

public class Window_4 {

  static final boolean ADD_SHUTTER_BOX = false;   // true: roller-shutter box above the window

  static final String TITLE = "Window digital twin: frame, sash, material memory and hinge settlement";
  static final String DESCRIPTION =
    "PURPOSE\n" +
    "This model follows how the openable sash of a window and its fixed frame drift out of alignment over the years. It combines heat transfer, the weights acting on room and window, and the aging of the soft parts that connect wall, frame, sash and glass. In this version the soft parts have MEMORY: what happened earlier (how long and how hard a gasket was squeezed, how warm it was) affects their state today.\n" +
    "\n" +
    "GEOMETRY (all sizes are the strain-free sizes at T_ref = 20 deg C)\n" +
    "- Room W_in x H_in x L_in inside (default 4 x 3 x 4 m), 30 cm insulated masonry walls (U_wall = 0.8), 20 cm concrete floor and ceiling slabs, concrete lintel over the opening.\n" +
    "- Window in the middle of the wall depth: fixed frame 1.23 x 1.48 m (50 mm face, 75 mm deep) in a 12 mm PU-foam joint; openable sash with a 5 mm rebate gap; EPDM centre gasket; hinges on the left (corner bearing = 2 blocks, scissor stay = 1 block) and 4 locking points in the room-side 20 mm of the gap; double glazing 4-14-4 (22 mm) on 4 mm EPDM bedding with 4 setting blocks (2 load-bearing placed diagonally, 2 distance blocks).\n" +
    "- Heater 3.0 x 0.40 x 0.106 m, 4 cm in front of the wall; heat transfer only.\n" +
    "- Frame, sash and glazing are homogenised solids: conductivity from Uf and Ug, stiffness from the profile moment of inertia (I_fr, I_sa) and from the two 4 mm panes.\n" +
    "\n" +
    "HEAT TRANSFER (time-dependent in Study 1, so wall and window heat capacity count)\n" +
    "- Conduction in all solids; wall, frame and glazing conductivity k = d/(1/U - Rsi - Rse), so each element reaches its U-value.\n" +
    "- Room-side surfaces: room air T_in = 20 deg C held by a thermostat, h_in = 7.69 W/(m^2*K) (Rsi = 0.13, still air).\n" +
    "- Outside surfaces: h_out = 25 W/(m^2*K) (Rse = 0.04, wind-swept) to T_out_v: monthly mean curve T_mean - A_seas*cos(2*pi*(t/1[a] + t_inst)) in Study 1, -5 deg C in Study 2.\n" +
    "- Neighbour rooms 18 deg C (T_adj) on side and back walls, ceiling top and floor bottom; e_left ... e_down = 1 switches a side to outdoor air.\n" +
    "- Heater: P_dem_v = P_heat*(T_in - T_out_v)/(T_in - T_design), never below 0, P_heat = q_vol*room volume; 4 cm air gap to the wall as an effective solid.\n" +
    "- Sun: absorbed heat = absorptance * G_t on facade, frame/sash and glazing; Study 2: off; Study 1: G_mean, a constant daily mean (127 W/m^2 is a horizontal all-hours mean, an upper value for a facade). Sunlight is heat only; UV aging is not modelled.\n" +
    "\n" +
    "SOLID MECHANICS (quasi-static: slow loads, no inertia)\n" +
    "- All solids except heater and air gap; floor slab on rollers; rigid-motion suppression on the concrete.\n" +
    "- Loads, all multiplied by the start-up ramp f_ramp (0 to 1 during t_ramp = 1 day): window self-weight; building self-weight and 12 kPa dead load of the storeys above (f_dead); 3 kPa live load (f_live); wind 0.5 kPa (f_wind, 0 = off).\n" +
    "- Thermal expansion (Multiphysics > Thermal Expansion): every part changes size by alpha*(T - T_ref), T_ref = 20 deg C, at its own temperature.\n" +
    "- Locks (Linear Elastic Material 3, orthotropic): stiff out of plane (E0_lk), so they press the sash onto the gasket; soft in plane (E_lk_ip, G_lk_ip), because the cams slide in their keepers. The sash weight is therefore carried by the hinges, as in a real window.\n" +
    "\n" +
    "AGING\n" +
    "- Soft parts (gasket, bedding, PU foam, hinges, locks, setting blocks): Linear Elastic Material 2 holds the long-term stiffness E_inf = r*E0; its Viscoelasticity sub-node adds one relaxing branch (shear modulus Gv_loc, relaxation time tau_loc = tau*1 a), so the stiffness right after loading equals E0 and relaxes towards r*E0. Each soft group has its own E0, r, tau and Poisson ratio in the domain variable groups 'Soft part: ...'. Because COMSOL integrates the branch strains in time, the state is carried forward: earlier loads keep acting, stresses relax, and parts partly recover when the load drops.\n" +
    "- Masonry and concrete: effective modulus E0/(1 + phi*t/(t + tau_cr)), t in years (EN 1996-1-1 eq. 3.8, EN 1992-1-1 eq. 7.20). They carry the constant dead load, for which this works well, so they have no memory.\n" +
    "- Densities, conductivities, expansion coefficients, geometry and loads never age.\n" +
    "\n" +
    "HINGE SETTLEMENT (the cumulative part)\n" +
    "- Viscoelastic relaxation is bounded and partly recovers, so on its own it gives only a small drift. Real sashes drop mainly through wear and play in the hinges and permanent set of the setting blocks. The model prescribes this as built-in strains (Linear Elastic Material 2 > Initial Stress and Strain) that grow with s_set = f_set*(1 - exp(-(t/1[a] - t0_ref)/tau_set)), zero at installation.\n" +
    "- Corner bearing (bottom block): sinks by d_cb_set = dv_cb_inf*s_set. Scissor stay (top block): play lets the sash top move away from the jamb by d_ss_set = dh_ss_inf*s_set. Load-bearing setting blocks: permanent set d_sb_set = d_sb_inf*s_set each.\n" +
    "- dv_cb_inf, dh_ss_inf, d_sb_inf and tau_set are ASSUMPTIONS: calibrate them with measured sash drop or with the adjustment intervals of the fitter. f_set = 0 switches the settlement off.\n" +
    "- The model then computes the consequences: how the lock corner drops (sag_lock), which gaps close (g_*), when the sash starts to rub (rub_margin < 0).\n" +
    "\n" +
    "STUDIES\n" +
    "- Study 1, aging with memory (one click): time-dependent from t = 0 to t_end years, output at 0, 0.002, 0.01, 0.05 years and then every dt_out years. At t = 0 the model is unloaded at 20 deg C; loads ramp up during the first day and the temperatures settle within days. The state at t0_ref = 0.05 years (about 18 days) is the installation reference: from there everything is carried forward in time, nothing is restarted.\n" +
    "- Study 2, design winter day check: one stationary solve at -5 deg C, building weight off, Linear Elastic Material 2 switched off so the soft parts use their new stiffness E0.\n" +
    "\n" +
    "OUTPUTS (Definitions > Frame-sash alignment, Checks)\n" +
    "- g_top_L ... g_lck_T: change of the 5 mm rebate gap at both ends of each side, positive opens; rub_margin = 5 mm + largest closure (below 0: the sash rubs); sag_lock: vertical drop of the lock-side bottom corner relative to the frame; skew_sash, skew_frame; dz_BL ... dz_TL, dz_max: out-of-plane step sash-frame (gasket); twist_rel.\n" +
    "- Checks: v_lintel (mean vertical displacement of the lintel underside), T_frame, T_sash, T_glass, P_dem_v.\n" +
    "- Table 'Aging: change since installation' and the 1D plot show value(t) - value(t0_ref) over time; the plot 'Hinge settlement (input) and sash response' compares the prescribed settlement with the computed sash drop.\n" +
    "- 3D plot 'Aging drift since installation, window only (scaled)': displacement minus its value at t0_ref, deformation x20, fixed colour range. Its animation shows only the winters (t = 1, 2, ..., 10 years), so the seasonal swing does not hide the drift.\n" +
    "\n" +
    "HOW TO USE\n" +
    "- Change dt_out and t_end to refine or extend the aging; press Compute on Study 1. Study 2 runs separately.\n" +
    "- The animation 'aging drift, winters only' uses the stored outputs 4, 8, ..., 44 (t = 0.05 and the winters 1 ... 10) for dt_out = 0.25 and t_end = 10; for other values choose the winter times in the animation by hand. Its colour range (0 to 1.6 mm) and deformation scale (x20) are fixed so frames can be compared; adjust them if you change the settlement values.\n" +
    "- If the build printed a NOTE about the viscoelastic node: open Solid Mechanics > Linear Elastic Material 2 > Viscoelasticity 1, choose Material model Standard linear solid (or Generalized Maxwell with one branch), enter shear modulus Gv_loc and relaxation time tau_loc, and enable the node.\n" +
    "- After changing geometry parameters (for example L_in), rebuild geometry and mesh and compute again.\n" +
    "\n" +
    "LIMITS\n" +
    "- The viscoelastic values r and tau and the settlement values are assumptions until fitted to tests and measurements. Hinge wear is prescribed, not computed from opening cycles. Sun as a constant daily mean; masonry and concrete without memory; hinges are blocks with one stiffness in all directions.";

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
    model.label("Window_4.mph");
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
      {"L_sb", "0.10[m]", "Setting block length"},
      {"a_sb", "0.05[m]", "Setting block distance from the glass corner"},
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
      {"zg0", "z_fc-t_igu/2", "Glass z start"},
      {"xs0", "xg0-g_e", "Sash rebate x start"},
      {"ys0", "yg0-g_e", "Sash rebate y start"},
      {"W_s", "W_g+2*g_e", "Sash rebate width"},
      {"H_s", "H_g+2*g_e", "Sash rebate height"},
      {"t_s", "t_igu+2*t_bed", "Sash rebate thickness"},
      {"zs0", "z_fc-t_s/2", "Sash rebate z start"},
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
      {"tau_set", "3", "Hinge settlement time constant (years): 63 % after tau_set, 95 % after 3*tau_set"},
      {"dv_cb_inf", "0.8[mm]", "Final sinking of the corner bearing (pin and bushing wear): ASSUMPTION to calibrate"},
      {"dh_ss_inf", "0.8[mm]", "Final play of the scissor stay (sash top moves away from the jamb): ASSUMPTION"},
      {"d_sb_inf", "0.3[mm]", "Final permanent set of each load-bearing setting block: ASSUMPTION"},
      {"E_lk_ip", "0.2[MPa]", "Locks: in-plane stiffness (cams slide in their keepers)"},
      {"G_lk_ip", "0.1[MPa]", "Locks: in-plane shear stiffness"},
      {"G_lk_z", "E0_lk/(2*(1+0.3))", "Locks: out-of-plane shear stiffness (they press the sash onto the gasket)"},
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
      {"P_heat", "q_vol*W_in*H_in*L_in", "Design heater power (3000 W)"},
      {"f_sun", "0", "Design day: sun on (1) / off (0)"},
      {"G_sun", "400[W/m^2]", "Design-day irradiance on the facade"},
      {"G_mean", "127[W/m^2]", "Daily-mean irradiance (aging study): all-hours mean on a horizontal surface in Germany 2024; a vertical facade receives less"},
      {"G_t", "(1-f_season)*f_sun*G_sun+f_season*G_mean", "Irradiance used"},
      {"a_wall", "0.6", "Solar absorptance, facade"},
      {"a_frame", "0.7", "Solar absorptance, frame and sash"},
      {"a_igu", "0.15", "Solar absorptance, glazing"},
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
      {"rho_fr", "1.5[kg/m]/(b_fr*d_f)", "Equivalent frame density"},
      {"rho_sa", "1.8[kg/m]/(b_sa*d_f)", "Equivalent sash density"},
      {"E_igu", "70[GPa]*2*t_g/t_igu", "Equivalent glazing modulus"},
      {"rho_igu", "2500[kg/m^3]*2*t_g/t_igu", "Equivalent glazing density"},
      {"E0_gk", "1[MPa]", "Centre gasket, initial (hollow EPDM)"},
      {"E0_bed", "5[MPa]", "Bedding EPDM, initial"},
      {"E0_jt", "3[MPa]", "PU foam joint incl. anchors, initial"},
      {"E0_hw", "2[GPa]", "Hinge with bushing, initial"},
      {"E0_lk", "0.5[GPa]", "Locking point, initial"},
      {"E0_sbl", "1[GPa]", "Load-bearing setting blocks, initial"},
      {"E0_sbd", "0.2[GPa]", "Distance blocks, initial"},
      {"r_epdm", "0.6", "EPDM long-term / initial modulus"},
      {"tau_epdm", "0.5", "EPDM relaxation time (years): viscoelastic branch"},
      {"r_foam", "0.5", "PU foam long-term / initial"},
      {"tau_foam", "1", "PU foam relaxation time (years)"},
      {"r_hw", "0.5", "Hinge bushing long-term / initial (incl. wear)"},
      {"tau_hw", "2", "Hinge relaxation time (years)"},
      {"r_sb", "0.7", "Setting blocks long-term / initial"},
      {"tau_sb", "2", "Setting block relaxation time (years)"},
      {"phi_mas", "1.0", "Masonry final creep coefficient"},
      {"phi_con", "2.0", "Concrete final creep coefficient"},
      {"tau_cr", "1", "Creep development time (years)"},

      // loads
      {"f_dead", "1", "Building self-weight and dead load: 1 in the aging study (default), 0 on the design day"},
      {"p_roof", "12[kPa]", "Dead load from upper storeys and roof"},
      {"f_live", "1", "Live load factor"},
      {"p_live", "3[kPa]", "Live load from upper storeys"},
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
    blk(model, "b_fi", "Frame inner opening", new String[]{"xfi0", "yfi0", "zf0-0.01[m]"}, new String[]{"W_fi", "H_fi", "d_f+0.02[m]"});
    dif(model, "frame", "Fixed frame (Blendrahmen)", "b_fe2", new String[]{"b_fi"});
    // sash
    blk(model, "b_se", "Sash envelope", new String[]{"xsa0", "ysa0", "zf0"}, new String[]{"W_sa", "H_sa", "d_f"});
    blk(model, "b_cl", "Clear opening", new String[]{"xc0", "yc0", "zf0-0.01[m]"}, new String[]{"W_c", "H_c", "d_f+0.02[m]"});
    blk(model, "b_sl", "Sash rebate", new String[]{"xs0", "ys0", "zs0"}, new String[]{"W_s", "H_s", "t_s"});
    dif(model, "sash", "Sash (Fluegel)", "b_se", new String[]{"b_cl", "b_sl"});
    // glazing in the sash
    blk(model, "b_sl2", "Sash rebate (for bedding)", new String[]{"xs0", "ys0", "zs0"}, new String[]{"W_s", "H_s", "t_s"});
    blk(model, "b_ig2", "Glazing (for bedding)", new String[]{"xg0", "yg0", "zg0"}, new String[]{"W_g", "H_g", "t_igu"});
    blk(model, "b_cl2", "Clear opening (for bedding)", new String[]{"xc0", "yc0", "zf0-0.01[m]"}, new String[]{"W_c", "H_c", "d_f+0.02[m]"});
    dif(model, "bed", "EPDM bedding", "b_sl2", new String[]{"b_ig2", "b_cl2"});
    blk(model, "igu", "Glazing 4-14-4", new String[]{"xg0", "yg0", "zg0"}, new String[]{"W_g", "H_g", "t_igu"});
    blk(model, "sbl1", "Load block, bottom hinge side", new String[]{"xg0+a_sb", "ys0", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbl2", "Load block, top lock side", new String[]{"xg0+W_g-a_sb-L_sb", "yg0+H_g", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbd1", "Distance block, bottom lock side", new String[]{"xg0+W_g-a_sb-L_sb", "ys0", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    blk(model, "sbd2", "Distance block, top hinge side", new String[]{"xg0+a_sb", "yg0+H_g", "zg0"}, new String[]{"L_sb", "g_e", "t_igu"});
    // centre gasket ring in the rebate gap
    blk(model, "b_gk1", "Gasket band outer", new String[]{"xfi0", "yfi0", "z_fc-d_gk/2"}, new String[]{"W_fi", "H_fi", "d_gk"});
    blk(model, "b_gk2", "Gasket band inner", new String[]{"xsa0", "ysa0", "z_fc-d_gk/2-0.01[m]"}, new String[]{"W_sa", "H_sa", "d_gk+0.02[m]"});
    dif(model, "gasket", "Centre gasket (EPDM)", "b_gk1", new String[]{"b_gk2"});
    // hinges on the left (room-side hardware zone)
    blk(model, "hw1", "Corner bearing, bottom", new String[]{"xsa0", "yfi0", "zf0"}, new String[]{"L_hw", "s_gap", "d_hw"});
    blk(model, "hw2", "Corner bearing, side", new String[]{"xfi0", "ysa0", "zf0"}, new String[]{"s_gap", "L_hw", "d_hw"});
    blk(model, "hw3", "Scissor stay, top", new String[]{"xfi0", "ysa0+H_sa-L_hw", "zf0"}, new String[]{"s_gap", "L_hw", "d_hw"});
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
    box(model, "s_fenv", "Window (frame envelope)", 3, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "zf0-tol", "zf0+d_f+tol");
    box(model, "s_fin", "Inside the frame opening", 3, "xfi0-tol", "xfi0+W_fi+tol", "yfi0-tol", "yfi0+H_fi+tol", "zf0-tol", "zf0+d_f+tol");
    sdiff(model, "s_frame", "Fixed frame", 3, new String[]{"s_fenv"}, new String[]{"s_fin"});
    box(model, "s_senv", "Sash envelope", 3, "xsa0-tol", "xsa0+W_sa+tol", "ysa0-tol", "ysa0+H_sa+tol", "zf0-tol", "zf0+d_f+tol");
    sdiff(model, "s_gapparts", "Rebate gap parts", 3, new String[]{"s_fin"}, new String[]{"s_senv"});
    box(model, "s_hwz", "Hardware zone", 3, "xfi0-tol", "xfi0+W_fi+tol", "yfi0-tol", "yfi0+H_fi+tol", "zf0-tol", "zf0+d_hw+tol");
    sdiff(model, "s_hw_all", "All hardware", 3, new String[]{"s_hwz"}, new String[]{"s_senv"});
    box(model, "s_hinge_z", "Hinge strip (left)", 3, "xfi0-tol", "xsa0+L_hw+tol", "yfi0-tol", "yfi0+H_fi+tol", "zf0-tol", "zf0+d_hw+tol");
    sdiff(model, "s_lock", "Locking points", 3, new String[]{"s_hw_all"}, new String[]{"s_hinge_z"});
    sdiff(model, "s_hinge", "Hinges", 3, new String[]{"s_hw_all"}, new String[]{"s_lock"});
    sdiff(model, "s_gasket", "Centre gasket", 3, new String[]{"s_gapparts"}, new String[]{"s_hw_all"});
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
    sdiff(model, "s_bed", "EPDM bedding", 3, new String[]{"s_slot"}, new String[]{"s_igu", "s_sbl", "s_sbd"});
    box(model, "s_jbox", "Opening at frame depth", 3, "xo0-tol", "xo0+W_o+tol", "yo0-tol", "yo0+H_o+tol", "zf0-tol", "zf0+d_f+tol");
    sdiff(model, "s_joint", "Installation joint", 3, new String[]{"s_jbox"}, new String[]{"s_fenv"});
    sdiff(model, "s_mech", "Structural domains", 3, new String[]{"s_all"}, new String[]{"s_heater", "s_gap"});
    sdiff(model, "s_bldg", "Building structure (no window)", 3, new String[]{"s_mech"}, new String[]{"s_fenv"});

    // ===================================================================== boundary selections
    model.component(C).selection().create("b_ext", "Adjacent");
    model.component(C).selection("b_ext").label("All outer boundaries");
    model.component(C).selection("b_ext").set("input", new String[]{"s_all"});
    model.component(C).selection("b_ext").set("outputdim", 2);
    model.component(C).selection("b_ext").set("interior", false);
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
    box(model, "b_outbox", "Facade region", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+tol", "z_fc", "tol");
    sint(model, "b_out", "Outdoor surfaces", 2, new String[]{"b_ext", "b_outbox"});
    box(model, "b_winbox_out", "Window region, outside", 2, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "z_fc", "zf0+d_f+tol");
    sint(model, "b_win_out", "Window, outside surfaces", 2, new String[]{"b_ext", "b_winbox_out"});
    box(model, "b_gbox", "Glass outer face", 2, "xg0-tol", "xg0+W_g+tol", "yg0-tol", "yg0+H_g+tol", "zg0+t_igu-tol", "zg0+t_igu+tol");
    sint(model, "b_glass_out", "Glazing, outer face", 2, new String[]{"b_ext", "b_gbox"});
    box(model, "b_fsbox", "Frame and sash outer plane", 2, "xf0-tol", "xf0+W_f+tol", "yf0-tol", "yf0+H_f+tol", "zf0+d_f-tol", "zf0+d_f+tol");
    sint(model, "b_frame_sun", "Frame and sash, sunlit faces", 2, new String[]{"b_ext", "b_fsbox"});
    sdiff(model, "b_facade", "Opaque facade", 2, new String[]{"b_out"}, new String[]{"b_win_out"});
    box(model, "b_floor", "Floor slab bottom", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "-t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_ceil", "Ceiling slab top", 2, "-t_w-tol", "W_in+t_w+tol", "H_in+t_slab-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_left", "Left wall, outer face", 2, "-t_w-tol", "-t_w+tol", "-t_slab-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_right", "Right wall, outer face", 2, "W_in+t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "tol");
    box(model, "b_back", "Back wall, outer face", 2, "-t_w-tol", "W_in+t_w+tol", "-t_slab-tol", "H_in+t_slab+tol", "-(L_in+2*t_w)-tol", "-(L_in+2*t_w)+tol");

    // evaluation points on the room-side face (z = zf0)
    String[][] pts = {
      {"fo_BL", "xf0", "yf0"}, {"fo_BR", "xf0+W_f", "yf0"}, {"fo_TR", "xf0+W_f", "yf0+H_f"}, {"fo_TL", "xf0", "yf0+H_f"},
      {"fi_BL", "xfi0", "yfi0"}, {"fi_BR", "xfi0+W_fi", "yfi0"}, {"fi_TR", "xfi0+W_fi", "yfi0+H_fi"}, {"fi_TL", "xfi0", "yfi0+H_fi"},
      {"sa_BL", "xsa0", "ysa0"}, {"sa_BR", "xsa0+W_sa", "ysa0"}, {"sa_TR", "xsa0+W_sa", "ysa0+H_sa"}, {"sa_TL", "xsa0", "ysa0+H_sa"}
    };
    for (String[] p : pts) pt(model, "p_" + p[0], p[1], p[2], "zf0");

    // ===================================================================== materials (later ones override)
    mat(model, "m_wall", "Masonry with insulation (U 0.8), creeping", null, "k_wall", "1400[kg/m^3]", "1000[J/(kg*K)]", "6e-6[1/K]", "3[GPa]/(1+phi_mas*t_age/(t_age+tau_cr))", "0.2");
    mat(model, "m_conc", "Concrete: slabs and lintel, creeping", "s_concrete", "2.0[W/(m*K)]", "2400[kg/m^3]", "1000[J/(kg*K)]", "10e-6[1/K]", "30[GPa]/(1+phi_con*t_age/(t_age+tau_cr))", "0.2");
    if (ADD_SHUTTER_BOX)
      mat(model, "m_sbox", "Roller-shutter box (hollow, insulated)", "s_sbox", "0.08[W/(m*K)]", "100[kg/m^3]", "1400[J/(kg*K)]", "60e-6[1/K]", "0.1[GPa]", "0.3");
    mat(model, "m_joint", "PU foam joint (new stiffness)", "s_joint", "0.035[W/(m*K)]", "30[kg/m^3]", "1400[J/(kg*K)]", "60e-6[1/K]", "E0_jt", "0.3");
    mat(model, "m_frame", "Fixed frame Al/PA, homogenised", "s_frame", "k_frame", "rho_fr", "900[J/(kg*K)]", "23.4e-6[1/K]", "E_fr", "0.33");
    mat(model, "m_sash", "Sash Al/PA, homogenised", "s_sash", "k_frame", "rho_sa", "900[J/(kg*K)]", "23.4e-6[1/K]", "E_sa", "0.33");
    mat(model, "m_gk", "Centre gasket EPDM (new stiffness)", "s_gasket", "0.25[W/(m*K)]", "1150[kg/m^3]", "2000[J/(kg*K)]", "160e-6[1/K]", "E0_gk", "0.45");
    mat(model, "m_hinge", "Hinges with bushings (new stiffness)", "s_hinge", "15[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "E0_hw", "0.3");
    mat(model, "m_lock", "Locking points (new stiffness)", "s_lock", "15[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "E0_lk", "0.3");
    mat(model, "m_bed", "Bedding EPDM (new stiffness)", "s_bed", "0.25[W/(m*K)]", "1150[kg/m^3]", "2000[J/(kg*K)]", "160e-6[1/K]", "E0_bed", "0.45");
    mat(model, "m_sbl", "Load-bearing setting blocks (new stiffness)", "s_sbl", "0.2[W/(m*K)]", "950[kg/m^3]", "1800[J/(kg*K)]", "100e-6[1/K]", "E0_sbl", "0.3");
    mat(model, "m_sbd", "Distance blocks (new stiffness)", "s_sbd", "0.2[W/(m*K)]", "950[kg/m^3]", "1800[J/(kg*K)]", "100e-6[1/K]", "E0_sbd", "0.3");
    mat(model, "m_igu", "Glazing 4-14-4, homogenised", "s_igu", "k_igu", "rho_igu", "750[J/(kg*K)]", "9e-6[1/K]", "E_igu", "0.22");
    mat(model, "m_heat", "Heater steel", "s_heater", "45[W/(m*K)]", "7850[kg/m^3]", "475[J/(kg*K)]", "12e-6[1/K]", "200[GPa]", "0.3");
    mat(model, "m_gap", "Air gap (effective)", "s_gap", "h_gap*g_h", "1.2[kg/m^3]", "1000[J/(kg*K)]", "0[1/K]", "1[kPa]", "0.3");

    // ===================================================================== climate, heater and load ramp as functions of time
    model.component(C).variable().create("var_t");
    model.component(C).variable("var_t").label("Climate, heater and start-up ramp in time");
    model.component(C).variable("var_t").set("t_age", "if(f_season>0.5, t/1[a], 0)", "Age in years: time in the aging study, 0 in the stationary design-day study (which has no t)");
    model.component(C).variable("var_t").set("T_out_v", "if(f_season>0.5, T_mean-A_seas*cos(2*pi*(t_age+t_inst)), T_design)", "Outdoor temperature (monthly mean curve, or design day)");
    model.component(C).variable("var_t").set("P_dem_v", "P_heat*max(0,(T_in-T_out_v)/(T_in-T_design))", "Heater power at the current outdoor temperature");
    model.component(C).variable("var_t").set("Q_heat_v", "P_dem_v/(L_h*H_h*D_h)", "Heater volumetric source");
    model.component(C).variable("var_t").set("f_ramp", "if(f_season>0.5, min(1, t_age*1[a]/t_ramp), 1)", "Start-up ramp of all loads (aging study)");
    model.component(C).variable("var_t").set("s_set", "f_set*(1-exp(-max(0, t_age-t0_ref)/tau_set))", "Hinge settlement progress (0 at installation, 1 in the long run)");
    model.component(C).variable("var_t").set("d_cb_set", "dv_cb_inf*s_set", "Corner bearing sinking now");
    model.component(C).variable("var_t").set("d_ss_set", "dh_ss_inf*s_set", "Scissor stay play now");
    model.component(C).variable("var_t").set("d_sb_set", "d_sb_inf*s_set", "Setting block permanent set now");

    // ===================================================================== soft parts: data for the viscoelastic (memory) model
    suni(model, "s_soft", "Soft parts (viscoelastic)", 3, new String[]{"s_gasket", "s_bed", "s_joint", "s_hinge", "s_lock", "s_sbl", "s_sbd"});
    String[][] soft = {{"vs_gk", "s_gasket", "Centre gasket", "E0_gk", "r_epdm", "tau_epdm", "0.45"},
                       {"vs_bed", "s_bed", "EPDM bedding", "E0_bed", "r_epdm", "tau_epdm", "0.45"},
                       {"vs_jt", "s_joint", "PU-foam joint", "E0_jt", "r_foam", "tau_foam", "0.3"},
                       {"vs_hw", "s_hinge", "Hinges", "E0_hw", "r_hw", "tau_hw", "0.3"},
                       {"vs_lk", "s_lock", "Locking points", "E0_lk", "r_hw", "tau_hw", "0.3"},
                       {"vs_sbl", "s_sbl", "Load-bearing setting blocks", "E0_sbl", "r_sb", "tau_sb", "0.3"},
                       {"vs_sbd", "s_sbd", "Distance blocks", "E0_sbd", "r_sb", "tau_sb", "0.3"}};
    for (String[] g : soft) {
      model.component(C).variable().create(g[0]);
      model.component(C).variable(g[0]).label("Soft part: " + g[2]);
      model.component(C).variable(g[0]).selection().geom("geom1", 3);
      model.component(C).variable(g[0]).selection().named(g[1]);
      model.component(C).variable(g[0]).set("E0_loc", g[3], "Stiffness right after loading");
      model.component(C).variable(g[0]).set("r_loc", g[4], "Long-term / initial stiffness");
      model.component(C).variable(g[0]).set("tau_loc", g[5] + "*1[a]", "Relaxation time of the branch");
      model.component(C).variable(g[0]).set("nu_loc", g[6], "Poisson ratio");
    }
    model.component(C).variable().create("vs_all");
    model.component(C).variable("vs_all").label("Soft parts: viscoelastic branch");
    model.component(C).variable("vs_all").selection().geom("geom1", 3);
    model.component(C).variable("vs_all").selection().named("s_soft");
    model.component(C).variable("vs_all").set("E_inf_loc", "r_loc*E0_loc", "Long-term Young's modulus (Linear Elastic Material 2)");
    model.component(C).variable("vs_all").set("K_inf_loc", "E_inf_loc/(3*(1-2*nu_loc))", "Long-term bulk modulus");
    model.component(C).variable("vs_all").set("G_inf_loc", "E_inf_loc/(2*(1+nu_loc))", "Long-term shear modulus");
    model.component(C).variable("vs_all").set("Gv_loc", "3*K_inf_loc*E0_loc/(9*K_inf_loc-E0_loc)-G_inf_loc", "Branch shear modulus: instantaneous Young's modulus = E0");

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
                     {"up", "b_ceil", "e_up"}, {"down", "b_floor", "e_down"}};
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
    model.component(C).physics("solid").create("rms1", "RigidMotionSuppression", 3);
    model.component(C).physics("solid").feature("rms1").selection().named("s_concrete");
    model.component(C).physics("solid").create("bl1", "BodyLoad", 3);
    model.component(C).physics("solid").feature("bl1").label("Building self-weight (f_dead)");
    model.component(C).physics("solid").feature("bl1").selection().named("s_bldg");
    model.component(C).physics("solid").feature("bl1").set("FperVol", new String[]{"0", "-f_ramp*f_dead*solid.rho*g_const", "0"});
    model.component(C).physics("solid").create("bl2", "BodyLoad", 3);
    model.component(C).physics("solid").feature("bl2").label("Window self-weight (always on)");
    model.component(C).physics("solid").feature("bl2").selection().named("s_fenv");
    model.component(C).physics("solid").feature("bl2").set("FperVol", new String[]{"0", "-f_ramp*solid.rho*g_const", "0"});
    bload(model, "bnl1", "Upper storeys: dead (f_dead) + live (f_live)", "b_ceil", new String[]{"0", "-f_ramp*(f_dead*p_roof+f_live*p_live)", "0"});
    bload(model, "bnl2", "Wind on the window (f_wind)", "b_win_out", new String[]{"0", "0", "-f_ramp*f_wind*p_wind"});

    // soft parts: long-term stiffness in Linear Elastic Material 2, memory in its Viscoelasticity sub-node
    model.component(C).physics("solid").create("lemm2", "LinearElasticModel", 3);
    model.component(C).physics("solid").feature("lemm2").label("Linear Elastic Material 2: soft parts, long-term stiffness + memory");
    model.component(C).physics("solid").feature("lemm2").selection().named("s_soft");
    try {
      model.component(C).physics("solid").feature("lemm2").set("E_mat", "userdef");
      model.component(C).physics("solid").feature("lemm2").set("E", "E_inf_loc");
      model.component(C).physics("solid").feature("lemm2").set("nu_mat", "userdef");
      model.component(C).physics("solid").feature("lemm2").set("nu", "nu_loc");
    } catch (Exception e) {
      System.out.println("NOTE: in Linear Elastic Material 2 set Young's modulus = E_inf_loc and Poisson's ratio = nu_loc (User defined). " + e.getMessage());
    }
    try {
      model.component(C).physics("solid").feature("lemm2").create("vis1", "Viscoelasticity", 3);
      model.component(C).physics("solid").feature("lemm2").feature("vis1").label("Viscoelasticity 1: one relaxing branch (memory)");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("MaterialModel", "SLS");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("shiftFunction", "none");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("Gv", "Gv_loc");
      model.component(C).physics("solid").feature("lemm2").feature("vis1").set("tauv", "tau_loc");
    } catch (Exception e) {
      try { model.component(C).physics("solid").feature("lemm2").feature("vis1").active(false); } catch (Exception e2) { }
      System.out.println("NOTE: Viscoelasticity 1 is switched OFF because it could not be set from Java. "
        + "Set Material model SLS, shear modulus Gv_loc, relaxation time tau_loc, then enable it. " + e.getMessage());
    }

    // hinge settlement: built-in strains growing with s_set(t), zero at installation
    String[][] sets = {{"set_cb", "s_cb_bot", "Hinge settlement: corner bearing sinks", "0", "-dv_cb_inf*s_set/s_gap"},
                       {"set_ss", "s_ss_top", "Hinge settlement: scissor stay play", "dh_ss_inf*s_set/s_gap", "0"},
                       {"set_sb", "s_sbl", "Hinge settlement: setting blocks take a permanent set", "0", "-d_sb_inf*s_set/g_e"}};
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
      {"twist_rel", "(dz_BL+dz_TR)-(dz_BR+dz_TL)", "Twist of the sash relative to the frame"}
    };
    for (String[] v : V) model.component(C).variable("var1").set(v[0], v[1], v[2]);
    box(model, "b_lin_bot", "Lintel underside (top of the opening)", 2, "xo0-tol", "xo0+W_o+tol", "yo0+H_o-tol", "yo0+H_o+tol", "-t_w-tol", "tol");
    String[][] avs = {{"av_frame", "s_frame", "3", "Average over the fixed frame"}, {"av_sash", "s_sash", "3", "Average over the sash"},
                      {"av_igu", "s_igu", "3", "Average over the glazing"}, {"av_lin", "b_lin_bot", "2", "Average over the lintel underside"}};
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

    // ===================================================================== mesh
    model.component(C).mesh("mesh1").feature("size").set("hauto", 5);
    model.component(C).mesh("mesh1").create("ftet1", "FreeTet");
    model.component(C).mesh("mesh1").feature("ftet1").create("size1", "Size");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").label("Window region");
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").selection().geom("geom1", 3);
    model.component(C).mesh("mesh1").feature("ftet1").feature("size1").selection().named("s_jbox");
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
    for (String st : new String[]{"std1", "std2"}) {
      try { model.study(st).createAutoSequences("all"); } catch (Exception e) {
        System.out.println("NOTE: solver for " + st + " will be generated on Compute.");
      }
    }
    try {
      model.sol("sol1").feature("t1").set("tstepsbdf", "intermediate");
    } catch (Exception e) {
      System.out.println("NOTE: optional: in Solution 1 > Time-Dependent Solver 1 set Steps taken by solver = Intermediate.");
    }

    // ===================================================================== results
    String outs = "skew_frame skew_sash sag_lock rub_margin g_min dz_max twist_rel g_top_R g_lck_T g_bot_R g_hin_T";
    String[] ex = outs.split(" ");
    String[][] checks = {{"d_cb_set", "mm"}, {"d_ss_set", "mm"}, {"d_sb_set", "mm"}, {"v_lintel", "mm"}, {"T_frame", "degC"}, {"T_sash", "degC"}, {"T_glass", "degC"}, {"P_dem_v", "W"}};
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
        model.result("pg4").selection().named("s_fenv");
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
