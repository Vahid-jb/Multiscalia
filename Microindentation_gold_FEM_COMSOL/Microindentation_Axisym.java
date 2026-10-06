/*
 * Microindentation_Axisym.java
 *
 * Spherical microindentation of annealed gold, 2D axisymmetric (COMSOL 6.3).
 *
 *   Sample   : Au, linear elastic + von Mises plasticity, linear isotropic hardening
 *   Indenter : diamond, linear elastic, spherical cap R_ind
 *   Contact  : frictionless, penalty
 *   Loading  : quasistatic load - hold - unload
 *              disp_ctrl = 1 -> displacement control (h_max)
 *              disp_ctrl = 0 -> force control (F_max)
 *
 * The indenter top is connected to a "load frame" through a boundary load:
 * a stiff spring k_stiff in displacement control, the prescribed force plus a
 * weak spring k_soft in force control. F_ind is the force transmitted to the
 * sample in both modes.
 *
 * Build:   comsolcompile Microindentation_Axisym.java
 *          then File > Open > Microindentation_Axisym.class in COMSOL
 * Modules: Structural Mechanics, Nonlinear Structural Materials
 */

import com.comsol.model.*;
import com.comsol.model.util.*;

public class Microindentation_Axisym {

  public static Model run() {
    Model model = ModelUtil.create("Model");
    model.label("Micro_Indentation_Au.mph");

    // Parameters
    model.param().set("disp_ctrl", "1", "Control mode (1 = displacement, 0 = force)");
    model.param().set("h_max", "2[um]", "Maximum depth");
    model.param().set("F_max", "100[mN]", "Maximum force");
    model.param().set("t_load", "20[s]", "Loading time");
    model.param().set("t_hold", "10[s]", "Hold time");
    model.param().set("t_unload", "20[s]", "Unloading time");
    model.param().set("t_end", "t_load+t_hold+t_unload", "Total time");
    model.param().set("n_ramp", "40", "Output steps per ramp");

    model.param().set("R_ind", "50[um]", "Tip radius");
    model.param().set("E_ind", "1141[GPa]", "Young's modulus, diamond");
    model.param().set("nu_ind", "0.07", "Poisson's ratio, diamond");
    model.param().set("rho_ind", "3515[kg/m^3]", "Density, diamond");

    model.param().set("W_s", "300[um]", "Sample radius");
    model.param().set("H_s", "300[um]", "Sample thickness");
    model.param().set("E_s", "79[GPa]", "Young's modulus, Au");
    model.param().set("nu_s", "0.42", "Poisson's ratio, Au");
    model.param().set("rho_s", "19300[kg/m^3]", "Density, Au");
    model.param().set("sigy_s", "30[MPa]", "Initial yield stress, Au (annealed)");
    model.param().set("Et_s", "0.4[GPa]", "Isotropic tangent modulus, Au");
    model.param().set("Ek_s", "0.4[GPa]", "Kinematic tangent modulus, Au");

    model.param().set("a_est", "sqrt(2*R_ind*h_max)", "Estimated contact radius");
    model.param().set("b_fine", "1.5*a_est", "Refinement zone size");
    model.param().set("b_vis", "3*a_est", "Close-up zone size (3D plots)");
    model.param().set("h_fine", "a_est/30", "Element size, refinement zone");
    model.param().set("h_src", "2*h_fine", "Element size, indenter surface");
    model.param().set("h_glob", "H_s/10", "Maximum element size");
    model.param().set("cap_h", "0.3*R_ind", "Indenter cap height");
    model.param().set("r_cap", "sqrt(R_ind^2-(R_ind-cap_h)^2)", "Indenter cap radius");
    model.param().set("A_top", "pi*r_cap^2", "Indenter top area");
    model.param().set("r_m", "0.5*r_cap", "Point on indenter arc, r");
    model.param().set("z_m", "R_ind-sqrt(R_ind^2-r_m^2)", "Point on indenter arc, z");
    model.param().set("tol_g", "1e-3[um]", "Selection tolerance");

    model.param().set("k_stiff", "1000*2*E_s*a_est", "Load frame stiffness, displacement control");
    model.param().set("k_soft", "0.01*F_max/h_max", "Stabilizing stiffness, force control");

    model.component().create("comp1", true);

    // Geometry (r, z)
    model.component("comp1").geom().create("geom1", 2);
    model.component("comp1").geom("geom1").axisymmetric(true);
    model.component("comp1").geom("geom1").lengthUnit("\u00b5m");
    model.component("comp1").mesh().create("mesh1");

    model.component("comp1").geom("geom1").create("r1", "Rectangle");
    model.component("comp1").geom("geom1").feature("r1").label("Sample");
    model.component("comp1").geom("geom1").feature("r1").set("size", new String[]{"W_s", "H_s"});
    model.component("comp1").geom("geom1").feature("r1").set("pos", new String[]{"0", "-H_s"});
    model.component("comp1").geom("geom1").create("r2", "Rectangle");
    model.component("comp1").geom("geom1").feature("r2").label("Refinement zone");
    model.component("comp1").geom("geom1").feature("r2").set("size", new String[]{"b_fine", "b_fine"});
    model.component("comp1").geom("geom1").feature("r2").set("pos", new String[]{"0", "-b_fine"});
    model.component("comp1").geom("geom1").create("r4", "Rectangle");
    model.component("comp1").geom("geom1").feature("r4").label("Close-up zone");
    model.component("comp1").geom("geom1").feature("r4").set("size", new String[]{"b_vis", "b_vis"});
    model.component("comp1").geom("geom1").feature("r4").set("pos", new String[]{"0", "-b_vis"});
    model.component("comp1").geom("geom1").create("uni1", "Union");
    model.component("comp1").geom("geom1").feature("uni1").label("Sample (partitioned)");
    model.component("comp1").geom("geom1").feature("uni1").selection("input").set("r1", "r2", "r4");
    model.component("comp1").geom("geom1").feature("uni1").set("intbnd", true);

    model.component("comp1").geom("geom1").create("c1", "Circle");
    model.component("comp1").geom("geom1").feature("c1").set("r", "R_ind");
    model.component("comp1").geom("geom1").feature("c1").set("pos", new String[]{"0", "R_ind"});
    model.component("comp1").geom("geom1").create("r3", "Rectangle");
    model.component("comp1").geom("geom1").feature("r3").set("size", new String[]{"R_ind", "2*cap_h"});
    model.component("comp1").geom("geom1").feature("r3").set("pos", new String[]{"0", "-cap_h"});
    model.component("comp1").geom("geom1").create("int1", "Intersection");
    model.component("comp1").geom("geom1").feature("int1").label("Indenter");
    model.component("comp1").geom("geom1").feature("int1").selection("input").set("c1", "r3");

    model.component("comp1").geom("geom1").feature("fin").set("action", "assembly");
    model.component("comp1").geom("geom1").feature("fin").set("createpairs", false);
    model.component("comp1").geom("geom1").run();

    // Selections: tag, label, dim, rmin, rmax, zmin, zmax
    String[][] boxes = {
      {"sel_sample_dom", "Sample", "2", "-tol_g", "W_s+tol_g", "-H_s-tol_g", "tol_g"},
      {"sel_fine_dom", "Refinement zone", "2", "-tol_g", "b_fine+tol_g", "-b_fine-tol_g", "tol_g"},
      {"sel_vis_dom", "Close-up zone", "2", "-tol_g", "b_vis+tol_g", "-b_vis-tol_g", "tol_g"},
      {"sel_ind_dom", "Indenter", "2", "-tol_g", "r_cap+tol_g", "-tol_g", "cap_h+tol_g"},
      {"sel_top", "Sample surface", "1", "-tol_g", "W_s+tol_g", "-tol_g", "tol_g"},
      {"sel_bottom", "Sample bottom", "1", "-tol_g", "W_s+tol_g", "-H_s-tol_g", "-H_s+tol_g"},
      {"sel_ind_top", "Indenter top", "1", "-tol_g", "r_cap+tol_g", "cap_h-tol_g", "cap_h+tol_g"}
    };
    for (String[] b : boxes) {
      model.component("comp1").selection().create(b[0], "Box");
      model.component("comp1").selection(b[0]).label(b[1]);
      model.component("comp1").selection(b[0]).set("entitydim", Integer.parseInt(b[2]));
      model.component("comp1").selection(b[0]).set("xmin", b[3]);
      model.component("comp1").selection(b[0]).set("xmax", b[4]);
      model.component("comp1").selection(b[0]).set("ymin", b[5]);
      model.component("comp1").selection(b[0]).set("ymax", b[6]);
      model.component("comp1").selection(b[0]).set("condition", "inside");
    }
    model.component("comp1").selection().create("sel_ind_arc", "Ball");
    model.component("comp1").selection("sel_ind_arc").label("Indenter surface");
    model.component("comp1").selection("sel_ind_arc").set("entitydim", 1);
    model.component("comp1").selection("sel_ind_arc").set("posx", "r_m");
    model.component("comp1").selection("sel_ind_arc").set("posy", "z_m");
    model.component("comp1").selection("sel_ind_arc").set("r", "100*tol_g");
    model.component("comp1").selection("sel_ind_arc").set("condition", "intersects");

    // Materials
    model.component("comp1").material().create("mat1", "Common");
    model.component("comp1").material("mat1").label("Au (annealed)");
    model.component("comp1").material("mat1").selection().named("sel_sample_dom");
    model.component("comp1").material("mat1").propertyGroup("def").set("density", "rho_s");
    model.component("comp1").material("mat1").propertyGroup().create("Enu", "Enu", "Young's modulus and Poisson's ratio");
    model.component("comp1").material("mat1").propertyGroup("Enu").set("E", "E_s");
    model.component("comp1").material("mat1").propertyGroup("Enu").set("nu", "nu_s");
    model.component("comp1").material("mat1").propertyGroup().create("ElastoplasticModel", "ElastoplasticModel", "Elastoplastic material model");
    model.component("comp1").material("mat1").propertyGroup("ElastoplasticModel").set("sigmags", "sigy_s");
    model.component("comp1").material("mat1").propertyGroup("ElastoplasticModel").set("Et", "Et_s");
    model.component("comp1").material("mat1").propertyGroup("ElastoplasticModel").set("Ek", "Ek_s");

    model.component("comp1").material().create("mat2", "Common");
    model.component("comp1").material("mat2").label("Diamond");
    model.component("comp1").material("mat2").selection().named("sel_ind_dom");
    model.component("comp1").material("mat2").propertyGroup("def").set("density", "rho_ind");
    model.component("comp1").material("mat2").propertyGroup().create("Enu", "Enu", "Young's modulus and Poisson's ratio");
    model.component("comp1").material("mat2").propertyGroup("Enu").set("E", "E_ind");
    model.component("comp1").material("mat2").propertyGroup("Enu").set("nu", "nu_ind");

    // Load program and load frame
    model.component("comp1").variable().create("var1");
    model.component("comp1").variable("var1").label("Load program");
    model.component("comp1").variable("var1").set("prog",
        "if(t<t_load, t/t_load, if(t<t_load+t_hold, 1, max(t_end-t,0[s])/t_unload))",
        "Normalized load program");
    model.component("comp1").variable("var1").set("delta_cmd", "h_max*prog", "Commanded depth");
    model.component("comp1").variable("var1").set("F_cmd", "F_max*prog", "Commanded force");
    model.component("comp1").variable("var1").set("f_drive",
        "(1-disp_ctrl)*(-F_cmd/A_top-k_soft/A_top*w)-disp_ctrl*k_stiff/A_top*(w+delta_cmd)",
        "Load frame traction, z component");

    String[][] cpl = {
      {"intop_ind", "Integration", "Integration, indenter top", "sel_ind_top"},
      {"intop_bot", "Integration", "Integration, sample bottom", "sel_bottom"},
      {"aveop_ind", "Average", "Average, indenter top", "sel_ind_top"}
    };
    for (String[] c : cpl) {
      model.component("comp1").cpl().create(c[0], c[1]);
      model.component("comp1").cpl(c[0]).label(c[2]);
      model.component("comp1").cpl(c[0]).selection().geom("geom1", 1);
      model.component("comp1").cpl(c[0]).selection().named(c[3]);
      model.component("comp1").cpl(c[0]).set("axisym", true);
    }

    model.component("comp1").variable().create("var2");
    model.component("comp1").variable("var2").label("Outputs");
    model.component("comp1").variable("var2").set("F_ind", "-intop_ind(f_drive)", "Indentation force");
    model.component("comp1").variable("var2").set("h_ind", "-aveop_ind(w)", "Indenter displacement");
    model.component("comp1").variable("var2").set("F_bot", "-intop_bot(solid.sz)", "Reaction force, sample bottom");

    // Solid Mechanics
    model.component("comp1").physics().create("solid", "SolidMechanics", "geom1");
    model.component("comp1").physics("solid").prop("StructuralTransientBehavior").set("StructuralTransientBehavior", "Quasistatic");
    model.component("comp1").physics("solid").feature("lemm1").label("Linear Elastic Material, indenter");

    model.component("comp1").physics("solid").create("lemm2", "LinearElasticModel", 2);
    model.component("comp1").physics("solid").feature("lemm2").label("Linear Elastic Material, sample");
    model.component("comp1").physics("solid").feature("lemm2").selection().named("sel_sample_dom");
    model.component("comp1").physics("solid").feature("lemm2").create("plsty1", "Plasticity", 2);
    model.component("comp1").physics("solid").feature("lemm2").feature("plsty1").set("IsotropicHardeningModel", "LinearIsotropicHardening");
    model.component("comp1").physics("solid").feature("lemm2").feature("plsty1").set("KinematicHardeningModel", "NoKinematicHardening");

    model.component("comp1").physics("solid").create("fix1", "Fixed", 1);
    model.component("comp1").physics("solid").feature("fix1").selection().named("sel_bottom");

    model.component("comp1").physics("solid").create("bndl1", "BoundaryLoad", 1);
    model.component("comp1").physics("solid").feature("bndl1").label("Load frame");
    model.component("comp1").physics("solid").feature("bndl1").selection().named("sel_ind_top");
    model.component("comp1").physics("solid").feature("bndl1").set("forceReferenceArea", new String[]{"0", "0", "f_drive"});

    model.component("comp1").pair().create("p1", "Contact", "geom1");
    model.component("comp1").pair("p1").label("Contact pair, indenter - sample");
    model.component("comp1").pair("p1").source().named("sel_ind_arc");
    model.component("comp1").pair("p1").destination().named("sel_top");
    model.component("comp1").physics("solid").feature("dcnt1").set("ContactMethodCtrl", "Penalty");

    // Mesh
    model.component("comp1").mesh("mesh1").feature("size").set("custom", "on");
    model.component("comp1").mesh("mesh1").feature("size").set("hmax", "h_glob");
    model.component("comp1").mesh("mesh1").feature("size").set("hmin", "h_fine/2");
    model.component("comp1").mesh("mesh1").feature("size").set("hgrad", "1.25");

    model.component("comp1").mesh("mesh1").create("map1", "Map");
    model.component("comp1").mesh("mesh1").feature("map1").selection().geom("geom1", 2);
    model.component("comp1").mesh("mesh1").feature("map1").selection().named("sel_fine_dom");
    model.component("comp1").mesh("mesh1").feature("map1").create("size1", "Size");
    model.component("comp1").mesh("mesh1").feature("map1").feature("size1").set("custom", "on");
    model.component("comp1").mesh("mesh1").feature("map1").feature("size1").set("hmax", "h_fine");
    model.component("comp1").mesh("mesh1").feature("map1").feature("size1").set("hmaxactive", true);

    model.component("comp1").mesh("mesh1").create("ftri1", "FreeTri");
    model.component("comp1").mesh("mesh1").feature("ftri1").create("size1", "Size");
    model.component("comp1").mesh("mesh1").feature("ftri1").feature("size1").selection().geom("geom1", 1);
    model.component("comp1").mesh("mesh1").feature("ftri1").feature("size1").selection().named("sel_ind_arc");
    model.component("comp1").mesh("mesh1").feature("ftri1").feature("size1").set("custom", "on");
    model.component("comp1").mesh("mesh1").feature("ftri1").feature("size1").set("hmax", "h_src");
    model.component("comp1").mesh("mesh1").feature("ftri1").feature("size1").set("hmaxactive", true);
    model.component("comp1").mesh("mesh1").run();

    // Study
    model.study().create("std1");
    model.study("std1").label("Load - hold - unload");
    model.study("std1").create("time", "Transient");
    model.study("std1").feature("time").set("tunit", "s");
    model.study("std1").feature("time").set("tlist",
        "range(0,t_load/(10*n_ramp),t_load/n_ramp) range(2*t_load/n_ramp,t_load/n_ramp,t_load) "
      + "range(t_load+t_hold/5,t_hold/5,t_load+t_hold) "
      + "range(t_load+t_hold+t_unload/n_ramp,t_unload/n_ramp,t_end)");
    model.study("std1").createAutoSequences("all");

    model.sol("sol1").feature("t1").set("timemethod", "bdf");
    model.sol("sol1").feature("t1").set("tstepsbdf", "strict");
    model.sol("sol1").feature("t1").feature("fc1").set("dtech", "hnlin");
    model.sol("sol1").feature("t1").feature("fc1").set("maxiter", 50);

    // Datasets
    if (!java.util.Arrays.asList(model.result().dataset().tags()).contains("dset1")) {
      model.result().dataset().create("dset1", "Solution");
      model.result().dataset("dset1").set("solution", "sol1");
    }
    String[][] subsets = {
      {"dset_vis", "Close-up zone", "sel_vis_dom", "rev_vis", "Revolution, close-up"},
      {"dset_ind", "Indenter", "sel_ind_dom", "rev_ind", "Revolution, indenter"},
      {"dset_smp", "Sample", "sel_sample_dom", "rev_smp", "Revolution, sample"}
    };
    for (String[] s : subsets) {
      model.result().dataset().create(s[0], "Solution");
      model.result().dataset(s[0]).label(s[1]);
      model.result().dataset(s[0]).set("solution", "sol1");
      model.result().dataset(s[0]).selection().geom("geom1", 2);
      model.result().dataset(s[0]).selection().named(s[2]);
      model.result().dataset().create(s[3], "Revolve2D");
      model.result().dataset(s[3]).label(s[4]);
      model.result().dataset(s[3]).set("data", s[0]);
      model.result().dataset(s[3]).set("startangle", -90);
      model.result().dataset(s[3]).set("revangle", 270);
    }

    // 1D plots: tag, label, y, y unit, x (empty = time), x unit
    String[][] globals = {
      {"pg1", "Force-depth curve", "F_ind", "mN", "h_ind", "um"},
      {"pg2", "Depth vs time", "h_ind", "um", "", ""},
      {"pg3", "Force vs time", "F_ind", "mN", "", ""}
    };
    for (String[] g : globals) {
      model.result().create(g[0], "PlotGroup1D");
      model.result(g[0]).label(g[1]);
      model.result(g[0]).set("data", "dset1");
      model.result(g[0]).create("glob1", "Global");
      model.result(g[0]).feature("glob1").set("expr", new String[]{g[2]});
      model.result(g[0]).feature("glob1").set("unit", new String[]{g[3]});
      if (!g[4].isEmpty()) {
        model.result(g[0]).feature("glob1").set("xdata", "expr");
        model.result(g[0]).feature("glob1").set("xdataexpr", g[4]);
        model.result(g[0]).feature("glob1").set("xdataunit", g[5]);
      }
    }

    model.result().create("pg4", "PlotGroup1D");
    model.result("pg4").label("Surface profile");
    model.result("pg4").set("data", "dset1");
    setTime(model, "pg4", "last");
    model.result("pg4").create("lngr1", "LineGraph");
    model.result("pg4").feature("lngr1").selection().named("sel_top");
    model.result("pg4").feature("lngr1").set("expr", "w");
    model.result("pg4").feature("lngr1").set("unit", "um");
    model.result("pg4").feature("lngr1").set("xdata", "expr");
    model.result("pg4").feature("lngr1").set("xdataexpr", "r");
    model.result("pg4").feature("lngr1").set("xdataunit", "um");

    // 2D plots
    String[][] maps = {
      {"pg5", "von Mises stress", "solid.mises", "MPa"},
      {"pg6", "Equivalent plastic strain", "solid.epe", ""}
    };
    for (String[] m : maps) {
      model.result().create(m[0], "PlotGroup2D");
      model.result(m[0]).label(m[1]);
      model.result(m[0]).set("data", "dset1");
      setTime(model, m[0], "last");
      model.result(m[0]).create("surf1", "Surface");
      model.result(m[0]).feature("surf1").set("expr", m[2]);
      if (!m[3].isEmpty()) model.result(m[0]).feature("surf1").set("unit", m[3]);
      model.result(m[0]).feature("surf1").create("def1", "Deform");
      model.result(m[0]).feature("surf1").feature("def1").set("scaleactive", true);
      model.result(m[0]).feature("surf1").feature("def1").set("scale", "1");
    }
    try {
      model.result("pg5").create("str1", "Streamline");
      model.result("pg5").feature("str1").set("expr", new String[]{"u", "w"});
      model.result("pg5").feature("str1").set("coloring", "uniform");
      model.result("pg5").feature("str1").set("color", "magenta");
    } catch (Exception e) {
      // streamlines are optional
    }

    // 3D plots (revolved, 90 deg cut-away, deformation x5)
    // tag, title, dataset, expression, unit, color table, time
    String[][] plots3d = {
      {"pg7", "von Mises stress at maximum load (deformation x5)", "rev_vis", "solid.mises", "MPa", "Prism", "max"},
      {"pg8", "Residual von Mises stress (deformation x5)", "rev_vis", "solid.mises", "MPa", "Prism", "last"},
      {"pg9", "Equivalent plastic strain (deformation x5)", "rev_vis", "solid.epe", "", "Thermal", "last"},
      {"pg10", "Sample at maximum load (deformation x5)", "rev_smp", "solid.mises", "MPa", "Prism", "max"},
      {"pg11", "Spherical microindentation of Au (deformation x5)", "rev_vis", "solid.mises", "MPa", "Prism", "all"}
    };
    for (String[] p : plots3d) {
      model.result().create(p[0], "PlotGroup3D");
      model.result(p[0]).label(p[1]);
      model.result(p[0]).set("data", p[2]);
      model.result(p[0]).set("titletype", "manual");
      model.result(p[0]).set("title", p[1]);
      model.result(p[0]).set("edges", false);
      setTime(model, p[0], p[6]);

      model.result(p[0]).create("surf1", "Surface");
      model.result(p[0]).feature("surf1").set("expr", p[3]);
      if (!p[4].isEmpty()) model.result(p[0]).feature("surf1").set("unit", p[4]);
      model.result(p[0]).feature("surf1").set("colortable", p[5]);
      model.result(p[0]).feature("surf1").create("def1", "Deform");
      model.result(p[0]).feature("surf1").feature("def1").set("scaleactive", true);
      model.result(p[0]).feature("surf1").feature("def1").set("scale", "5");

      model.result(p[0]).create("surf2", "Surface");
      model.result(p[0]).feature("surf2").label("Indenter");
      model.result(p[0]).feature("surf2").set("data", "rev_ind");
      model.result(p[0]).feature("surf2").set("expr", "1");
      model.result(p[0]).feature("surf2").set("coloring", "uniform");
      model.result(p[0]).feature("surf2").set("color", "gray");
      model.result(p[0]).feature("surf2").create("def1", "Deform");
      model.result(p[0]).feature("surf2").feature("def1").set("scaleactive", true);
      model.result(p[0]).feature("surf2").feature("def1").set("scale", "5");
    }

    // Elastic strain energy contours on the animated view
    model.result("pg11").create("con1", "Contour");
    model.result("pg11").feature("con1").set("expr", "solid.WsGp");
    model.result("pg11").feature("con1").set("number", 50);
    model.result("pg11").feature("con1").set("colortable", "Rainbow");
    model.result("pg11").feature("con1").create("def1", "Deform");
    model.result("pg11").feature("con1").feature("def1").set("scaleactive", true);
    model.result("pg11").feature("con1").feature("def1").set("scale", "5");

    model.result().export().create("anim1", "Animation");
    model.result().export("anim1").set("plotgroup", "pg11");
    model.result().export("anim1").set("target", "player");

    return model;
  }

  /** Selects the solution time shown by a plot group: "last", "max" (end of hold) or "all". */
  private static void setTime(Model model, String pg, String mode) {
    if (mode.equals("all")) return;
    try {
      if (mode.equals("last")) {
        model.result(pg).setIndex("looplevelinput", "last", 0);
      } else {
        model.result(pg).setIndex("looplevelinput", "interp", 0);
        model.result(pg).setIndex("interp", "t_load+t_hold", 0);
      }
    } catch (Exception e) {
      // keep the default time selection
    }
  }

  public static void main(String[] args) {
    run();
  }
}
