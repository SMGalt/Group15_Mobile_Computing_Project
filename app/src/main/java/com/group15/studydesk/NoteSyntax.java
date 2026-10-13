package com.group15.studydesk;

import java.util.*;
import java.util.regex.*;

/** MIT port of notebook/render maths.js and chem.js. Unknown grammar remains literal source. */
public final class NoteSyntax {
  static final Map<String, String> SUP = new HashMap<>(),
      SUB = new HashMap<>(),
      COMMANDS = new HashMap<>();
  static final Set<String> ELEMENTS =
      new HashSet<>(
          Arrays.asList(
              "H,He,Li,Be,B,C,N,O,F,Ne,Na,Mg,Al,Si,P,S,Cl,Ar,K,Ca,Sc,Ti,V,Cr,Mn,Fe,Co,Ni,Cu,Zn,Ga,Ge,As,Se,Br,Kr,Rb,Sr,Y,Zr,Nb,Mo,Tc,Ru,Rh,Pd,Ag,Cd,In,Sn,Sb,Te,I,Xe,Cs,Ba,La,Ce,Pr,Nd,Pm,Sm,Eu,Gd,Tb,Dy,Ho,Er,Tm,Yb,Lu,Hf,Ta,W,Re,Os,Ir,Pt,Au,Hg,Tl,Pb,Bi,Po,At,Rn,Fr,Ra,Ac,Th,Pa,U,Np,Pu,Am,Cm,Bk,Cf,Es,Fm,Md,No,Lr,Rf,Db,Sg,Bh,Hs,Mt,Ds,Rg,Cn,Nh,Fl,Mc,Lv,Ts,Og"
                  .split(",")));

  static {
    SUP.put("0", "⁰");
    SUP.put("1", "¹");
    SUP.put("2", "²");
    SUP.put("3", "³");
    SUP.put("4", "⁴");
    SUP.put("5", "⁵");
    SUP.put("6", "⁶");
    SUP.put("7", "⁷");
    SUP.put("8", "⁸");
    SUP.put("9", "⁹");
    SUP.put("+", "⁺");
    SUP.put("-", "⁻");
    SUP.put("=", "⁼");
    SUP.put("(", "⁽");
    SUP.put(")", "⁾");
    SUP.put("n", "ⁿ");
    SUP.put("i", "ⁱ");
    SUP.put("x", "ˣ");
    SUP.put("a", "ᵃ");
    SUP.put("b", "ᵇ");
    SUP.put("c", "ᶜ");
    SUP.put("d", "ᵈ");
    SUP.put("e", "ᵉ");
    SUP.put("f", "ᶠ");
    SUP.put("g", "ᵍ");
    SUP.put("h", "ʰ");
    SUP.put("j", "ʲ");
    SUP.put("k", "ᵏ");
    SUP.put("l", "ˡ");
    SUP.put("m", "ᵐ");
    SUP.put("o", "ᵒ");
    SUP.put("p", "ᵖ");
    SUP.put("r", "ʳ");
    SUP.put("s", "ˢ");
    SUP.put("t", "ᵗ");
    SUP.put("u", "ᵘ");
    SUP.put("v", "ᵛ");
    SUP.put("w", "ʷ");
    SUP.put("y", "ʸ");
    SUP.put("z", "ᶻ");
    SUB.put("0", "₀");
    SUB.put("1", "₁");
    SUB.put("2", "₂");
    SUB.put("3", "₃");
    SUB.put("4", "₄");
    SUB.put("5", "₅");
    SUB.put("6", "₆");
    SUB.put("7", "₇");
    SUB.put("8", "₈");
    SUB.put("9", "₉");
    SUB.put("+", "₊");
    SUB.put("-", "₋");
    SUB.put("=", "₌");
    SUB.put("(", "₍");
    SUB.put(")", "₎");
    SUB.put("a", "ₐ");
    SUB.put("e", "ₑ");
    SUB.put("h", "ₕ");
    SUB.put("i", "ᵢ");
    SUB.put("j", "ⱼ");
    SUB.put("k", "ₖ");
    SUB.put("l", "ₗ");
    SUB.put("m", "ₘ");
    SUB.put("n", "ₙ");
    SUB.put("o", "ₒ");
    SUB.put("p", "ₚ");
    SUB.put("r", "ᵣ");
    SUB.put("s", "ₛ");
    SUB.put("t", "ₜ");
    SUB.put("u", "ᵤ");
    SUB.put("v", "ᵥ");
    SUB.put("x", "ₓ");
    COMMANDS.put("alpha", "α");
    COMMANDS.put("beta", "β");
    COMMANDS.put("gamma", "γ");
    COMMANDS.put("delta", "δ");
    COMMANDS.put("epsilon", "ε");
    COMMANDS.put("zeta", "ζ");
    COMMANDS.put("eta", "η");
    COMMANDS.put("theta", "θ");
    COMMANDS.put("iota", "ι");
    COMMANDS.put("kappa", "κ");
    COMMANDS.put("lambda", "λ");
    COMMANDS.put("mu", "μ");
    COMMANDS.put("nu", "ν");
    COMMANDS.put("xi", "ξ");
    COMMANDS.put("pi", "π");
    COMMANDS.put("rho", "ρ");
    COMMANDS.put("sigma", "σ");
    COMMANDS.put("tau", "τ");
    COMMANDS.put("upsilon", "υ");
    COMMANDS.put("phi", "φ");
    COMMANDS.put("chi", "χ");
    COMMANDS.put("psi", "ψ");
    COMMANDS.put("omega", "ω");
    COMMANDS.put("Gamma", "Γ");
    COMMANDS.put("Delta", "Δ");
    COMMANDS.put("Theta", "Θ");
    COMMANDS.put("Lambda", "Λ");
    COMMANDS.put("Xi", "Ξ");
    COMMANDS.put("Pi", "Π");
    COMMANDS.put("Sigma", "Σ");
    COMMANDS.put("Upsilon", "Υ");
    COMMANDS.put("Phi", "Φ");
    COMMANDS.put("Psi", "Ψ");
    COMMANDS.put("Omega", "Ω");
    COMMANDS.put("int", "∫");
    COMMANDS.put("iint", "∬");
    COMMANDS.put("oint", "∮");
    COMMANDS.put("sum", "∑");
    COMMANDS.put("prod", "∏");
    COMMANDS.put("sqrt", "√");
    COMMANDS.put("cbrt", "∛");
    COMMANDS.put("infty", "∞");
    COMMANDS.put("partial", "∂");
    COMMANDS.put("nabla", "∇");
    COMMANDS.put("pm", "±");
    COMMANDS.put("mp", "∓");
    COMMANDS.put("times", "×");
    COMMANDS.put("cdot", "·");
    COMMANDS.put("div", "÷");
    COMMANDS.put("neq", "≠");
    COMMANDS.put("leq", "≤");
    COMMANDS.put("geq", "≥");
    COMMANDS.put("approx", "≈");
    COMMANDS.put("equiv", "≡");
    COMMANDS.put("propto", "∝");
    COMMANDS.put("to", "→");
    COMMANDS.put("gets", "←");
    COMMANDS.put("iff", "⇔");
    COMMANDS.put("implies", "⇒");
    COMMANDS.put("in", "∈");
    COMMANDS.put("notin", "∉");
    COMMANDS.put("subset", "⊂");
    COMMANDS.put("cup", "∪");
    COMMANDS.put("cap", "∩");
    COMMANDS.put("forall", "∀");
    COMMANDS.put("exists", "∃");
    COMMANDS.put("therefore", "∴");
    COMMANDS.put("because", "∵");
    COMMANDS.put("angle", "∠");
    COMMANDS.put("degree", "°");
    COMMANDS.put("perp", "⊥");
    COMMANDS.put("parallel", "∥");
    COMMANDS.put("ldots", "…");
    COMMANDS.put("cdots", "⋯");
    COMMANDS.put("sin", "sin");
    COMMANDS.put("cos", "cos");
    COMMANDS.put("tan", "tan");
    COMMANDS.put("sec", "sec");
    COMMANDS.put("csc", "csc");
    COMMANDS.put("cot", "cot");
    COMMANDS.put("arcsin", "arcsin");
    COMMANDS.put("arccos", "arccos");
    COMMANDS.put("arctan", "arctan");
    COMMANDS.put("sinh", "sinh");
    COMMANDS.put("cosh", "cosh");
    COMMANDS.put("tanh", "tanh");
    COMMANDS.put("log", "log");
    COMMANDS.put("ln", "ln");
    COMMANDS.put("lg", "lg");
    COMMANDS.put("exp", "exp");
    COMMANDS.put("lim", "lim");
    COMMANDS.put("max", "max");
    COMMANDS.put("min", "min");
    COMMANDS.put("det", "det");
    COMMANDS.put("dim", "dim");
    COMMANDS.put("ker", "ker");
    COMMANDS.put("deg", "deg");
    COMMANDS.put("gcd", "gcd");
    COMMANDS.put("mod", "mod");
  }

  public record Node(String type, String text, List<Node> first, List<Node> second) {}

  private record Arg(String body, int next) {}

  private static Arg arg(String s, int i) {
    if (i >= s.length()) return null;
    if (s.charAt(i) != '{') return new Arg(s.substring(i, i + 1), i + 1);
    int depth = 0;
    for (int k = i; k < s.length(); k++) {
      char c = s.charAt(k);
      if (c == '{') depth++;
      if (c == '}' && --depth == 0) return new Arg(s.substring(i + 1, k), k + 1);
    }
    return null;
  }

  static String map(String s, Map<String, String> table) {
    StringBuilder out = new StringBuilder();
    for (char ch : s.toCharArray()) {
      String v = table.get(String.valueOf(ch));
      if (v == null) return null;
      out.append(v);
    }
    return out.toString();
  }

  public static List<Node> maths(String src) {
    if (src.trim().isEmpty()) return null;
    List<Node> nodes = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    int i = 0;
    while (i < src.length()) {
      char c = src.charAt(i);
      if (c == '\\') {
        Matcher match = Pattern.compile("^\\\\([A-Za-z]+)").matcher(src.substring(i));
        if (!match.find()) return null;
        String name = match.group(1);
        i += match.group().length();
        if (Arrays.asList("frac", "tfrac", "dfrac", "binom").contains(name)) {
          Arg a = arg(src, i);
          if (a == null) return null;
          Arg b = arg(src, a.next);
          if (b == null) return null;
          List<Node> first = maths(a.body), second = maths(b.body);
          if (first == null || second == null) return null;
          flush(nodes, buffer);
          nodes.add(new Node(name.equals("binom") ? "binom" : "frac", "", first, second));
          i = b.next;
          continue;
        }
        if (Arrays.asList("sqrt", "cbrt", "overline", "vec", "hat", "bar").contains(name)) {
          Arg a = arg(src, i);
          if (a == null) return null;
          List<Node> body = maths(a.body);
          if (body == null) return null;
          flush(nodes, buffer);
          nodes.add(
              new Node(
                  name.equals("sqrt") || name.equals("cbrt") ? "root" : "accent",
                  name,
                  body,
                  null));
          i = a.next;
          continue;
        }
        String glyph = COMMANDS.get(name);
        if (glyph == null) return null;
        buffer.append(glyph);
        if (glyph.matches("[a-z]{2,}")) buffer.append(' ');
        continue;
      }
      if (c == '^' || c == '_') {
        Arg a = arg(src, i + 1);
        if (a == null) return null;
        String mapped = map(a.body, c == '^' ? SUP : SUB);
        if (mapped == null) return null;
        buffer.append(mapped);
        i = a.next;
        continue;
      }
      if (c != '{' && c != '}') buffer.append(c);
      i++;
    }
    flush(nodes, buffer);
    return nodes.isEmpty() ? null : nodes;
  }

  private static void flush(List<Node> n, StringBuilder b) {
    if (b.length() > 0) {
      n.add(new Node("text", b.toString(), null, null));
      b.setLength(0);
    }
  }

  public static String flatten(List<Node> nodes) {
    if (nodes == null) return "";
    StringBuilder b = new StringBuilder();
    for (Node n : nodes)
      switch (n.type) {
        case "text" -> b.append(n.text);
        case "frac", "binom" ->
            b.append("(")
                .append(flatten(n.first))
                .append("/")
                .append(flatten(n.second))
                .append(")");
        case "root" -> b.append("√(").append(flatten(n.first)).append(")");
        case "accent" -> b.append(flatten(n.first));
      }
    return b.toString();
  }

  public record ChemToken(String kind, String text, int value) {}

  public static List<ChemToken> species(String input) {
    String s = input.trim();
    if (s.isEmpty()) return null;
    List<ChemToken> out = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
      String rest = s.substring(i);
      Matcher state = Pattern.compile("^\\((aq|s|g|l)\\)").matcher(rest);
      if (state.find()) {
        out.add(new ChemToken("state", state.group(), 0));
        i += state.group().length();
        continue;
      }
      char open = s.charAt(i);
      if (open == '(' || open == '[') {
        char close = open == '(' ? ')' : ']';
        int depth = 0, k = i;
        for (; k < s.length(); k++) {
          if (s.charAt(k) == open) depth++;
          else if (s.charAt(k) == close && --depth == 0) break;
        }
        if (k == s.length()) return null;
        List<ChemToken> inner = species(s.substring(i + 1, k));
        if (inner == null) return null;
        out.add(new ChemToken("open", String.valueOf(open), 0));
        out.addAll(inner);
        out.add(new ChemToken("close", String.valueOf(close), 0));
        i = k + 1;
        Matcher count = Pattern.compile("^([0-9]+)").matcher(s.substring(i));
        if (count.find()) {
          out.add(new ChemToken("count", map(count.group(), SUB), Integer.parseInt(count.group())));
          i += count.group().length();
        }
        continue;
      }
      Matcher charge = Pattern.compile("^([0-9]*)([+-])$").matcher(rest);
      if (charge.find()) {
        out.add(
            new ChemToken(
                "charge",
                map(charge.group(), SUP),
                (charge.group(1).isEmpty() ? 1 : Integer.parseInt(charge.group(1)))
                    * (charge.group(2).equals("+") ? 1 : -1)));
        i = s.length();
        continue;
      }
      if (open == '^') {
        Matcher forced = Pattern.compile("^\\^([0-9]*[+-]?)").matcher(rest);
        if (!forced.find() || forced.group(1).isEmpty()) return null;
        String raw = forced.group(1), mag = raw.replaceAll("[+-]$", "");
        int value =
            raw.endsWith("+") || raw.endsWith("-")
                ? (mag.isEmpty() ? 1 : Integer.parseInt(mag)) * (raw.endsWith("+") ? 1 : -1)
                : 0;
        out.add(new ChemToken("charge", map(raw, SUP), value));
        i += forced.group().length();
        continue;
      }
      String symbol =
          i + 2 <= s.length() && ELEMENTS.contains(s.substring(i, i + 2))
              ? s.substring(i, i + 2)
              : s.substring(i, i + 1);
      if (!ELEMENTS.contains(symbol)) return null;
      out.add(new ChemToken("element", symbol, 0));
      i += symbol.length();
      Matcher trailing = Pattern.compile("^([0-9]+)([+-])$").matcher(s.substring(i));
      if (trailing.find()) {
        out.add(
            new ChemToken(
                "charge",
                map(trailing.group(), SUP),
                Integer.parseInt(trailing.group(1)) * (trailing.group(2).equals("+") ? 1 : -1)));
        i = s.length();
        continue;
      }
      Matcher count = Pattern.compile("^([0-9]+)").matcher(s.substring(i));
      if (count.find()) {
        out.add(new ChemToken("count", map(count.group(), SUB), Integer.parseInt(count.group())));
        i += count.group().length();
      }
    }
    return out.isEmpty() ? null : out;
  }

  public record Chem(
      String display, Map<String, Integer> left, Map<String, Integer> right, boolean equation) {
    public boolean balanced() {
      return !equation || left.equals(right);
    }
  }

  private static Map<String, Integer> count(List<ChemToken> tokens, int coefficient) {
    Map<String, Integer> frame = new LinkedHashMap<>();
    Deque<Map<String, Integer>> stack = new ArrayDeque<>();
    int charge = 0;
    for (int i = 0; i < tokens.size(); i++) {
      ChemToken token = tokens.get(i);
      if (token.kind.equals("open")) {
        stack.push(frame);
        frame = new LinkedHashMap<>();
      } else if (token.kind.equals("close")) {
        int mult =
            i + 1 < tokens.size() && tokens.get(i + 1).kind.equals("count")
                ? tokens.get(++i).value
                : 1;
        Map<String, Integer> parent = stack.isEmpty() ? new LinkedHashMap<>() : stack.pop();
        for (String element : frame.keySet())
          parent.merge(element, frame.get(element) * mult, Integer::sum);
        frame = parent;
      } else if (token.kind.equals("element")) {
        int n =
            i + 1 < tokens.size() && tokens.get(i + 1).kind.equals("count")
                ? tokens.get(++i).value
                : 1;
        frame.merge(token.text, n, Integer::sum);
      } else if (token.kind.equals("charge")) charge += token.value;
    }
    Map<String, Integer> atoms = new LinkedHashMap<>();
    for (String element : frame.keySet()) atoms.put(element, frame.get(element) * coefficient);
    if (charge != 0) atoms.put("q", charge * coefficient);
    return atoms;
  }

  private record Side(String display, Map<String, Integer> atoms) {}

  private static Side side(String raw) {
    StringBuilder display = new StringBuilder();
    Map<String, Integer> total = new LinkedHashMap<>();
    for (String part : raw.split("\\s\\+\\s|\\s\\+$|^\\+\\s")) {
      part = part.trim();
      if (part.isEmpty()) continue;
      Matcher coefficient = Pattern.compile("^([0-9]+)\\s*(.+)$").matcher(part);
      int n = 1;
      String body = part;
      if (coefficient.find()) {
        n = Integer.parseInt(coefficient.group(1));
        body = coefficient.group(2);
      }
      List<ChemToken> tokens = species(body);
      if (tokens == null) return null;
      if (display.length() > 0) display.append(" + ");
      if (n != 1) display.append(n);
      for (ChemToken token : tokens) display.append(token.text);
      Map<String, Integer> atoms = count(tokens, n);
      for (String element : atoms.keySet()) total.merge(element, atoms.get(element), Integer::sum);
    }
    return display.length() == 0 ? null : new Side(display.toString(), total);
  }

  public static Chem chemistry(String src) {
    try {
      return parseChemistry(src);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private static Chem parseChemistry(String src) {
    Matcher arrow = Pattern.compile("(<->|<=>|->|=>)").matcher(src);
    if (arrow.find()) {
      Side l = side(src.substring(0, arrow.start())), r = side(src.substring(arrow.end()));
      if (l != null && r != null)
        return new Chem(
            l.display
                + (arrow.group().equals("->") || arrow.group().equals("=>") ? " → " : " ⇌ ")
                + r.display,
            l.atoms,
            r.atoms,
            true);
    }
    List<ChemToken> tokens = species(src);
    if (tokens == null) return null;
    StringBuilder display = new StringBuilder();
    for (ChemToken token : tokens) display.append(token.text);
    return new Chem(display.toString(), new LinkedHashMap<>(), new LinkedHashMap<>(), false);
  }
}
