# -*- coding: utf-8 -*-
"""离线渲染桌面卡片「设计图」，不依赖模拟器 / adb。

做的事：
  1. 读 res/layout/widget_*.xml 与 res/xml/widget_*_info.xml（格子数）；
  2. 用字体真实度量做一次极简 measure/layout（LinearLayout 权重、gravity、
     FrameLayout 居中、RelativeLayout 两端对齐），按 dp 尺寸画到 PNG；
  3. 打印排版诊断：内容超框、文字被 maxLines 截断、行数、余量。

尺寸按「格子边长」推：卡片宽 = 列数 × 格子，高 = 行数 × 格子；
正方形款（代码里 min(宽,高) 定边长）因此边长正好等于 行/列 × 格子。
三档格子边长：55dp（老版本最小）、74dp（常见手机）、92dp（大屏 / 平板）。

用法：
  python tools/render_widget_designs.py                 # 全部 14 张 × 三档
  python tools/render_widget_designs.py --only combo    # 只渲某一张
  python tools/render_widget_designs.py --scale 6       # 放大倍数（默认 4）
输出：build/widget-designs/*.png（逐张三档拼图 + 单张）
"""
import os
import re
import sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
OUT = os.path.join(ROOT, "build", "widget-designs")

FONT_REG = "C:/Windows/Fonts/msyh.ttc"       # 微软雅黑 ≈ Noto Sans CJK
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"

PROFILES = [("min55", 55), ("phone74", 74), ("big92", 92)]
SCALE = 4.0   # 1dp = 4px

_fonts = {}


def font(size_px, bold=False):
    key = (round(size_px, 1), bold)
    if key not in _fonts:
        path = FONT_BOLD if bold and os.path.exists(FONT_BOLD) else FONT_REG
        _fonts[key] = ImageFont.truetype(path, max(1, int(round(size_px))))
    return _fonts[key]


# ───────────────────────── 资源解析 ─────────────────────────

def load_colors():
    colors = {}
    p = os.path.join(RES, "values", "widget_colors.xml")
    root = ET.parse(p).getroot()
    for c in root.findall("color"):
        colors[c.get("name")] = c.text.strip()
    return colors


COLORS = load_colors()


def parse_color(v, default=None):
    if not v:
        return default
    v = v.strip()
    if v.startswith("#"):
        h = v[1:]
        if len(h) == 6:
            return "#" + h
        if len(h) == 8:                     # #AARRGGBB
            return "#" + h[2:]
        return default
    m = re.match(r"@color/(\w+)", v)
    if m:
        return COLORS.get(m.group(1), default)
    return default


def gflag(gravity, flag):
    """gravity 分词判断：避免 'center' 误命中 'center_vertical'"""
    return flag in (gravity or "").replace("|", " ").split()


def aname(k):
    """{http://schemas.android.com/apk/res/android}textSize -> textSize"""
    return k.split("}")[-1].split(":")[-1]


def load_shape(name):
    """返回 dict(fill=color, stroke=color, sw=dp, radius=dp, oval=bool)；找不到则 None"""
    for folder in ("drawable", "drawable-nodpi"):
        p = os.path.join(RES, folder, name + ".xml")
        if os.path.exists(p):
            root = ET.parse(p).getroot()
            tag = root.tag
            if tag == "vector":
                return {"vector": True}
            shape_el = root if tag == "shape" else root.find(".//shape")
            if shape_el is None:
                # layer-list: 取第一层 shape 的实色
                first = root.find(".//item/shape")
                if first is None:
                    return None
                shape_el = first

            def sattr(el, key, dflt=None):
                for k, v in el.attrib.items():
                    if aname(k) == key:
                        return v
                return dflt

            fill = None
            solid = shape_el.find("solid")
            if solid is not None:
                fill = parse_color(sattr(solid, "color"))
            stroke_c, stroke_w = None, 0.0
            st = shape_el.find("stroke")
            if st is not None:
                stroke_c = parse_color(sattr(st, "color"))
                stroke_w = dp(sattr(st, "width", "0dp"))
            radius = 0.0
            cr = shape_el.find("corners")
            if cr is not None:
                radius = dp(sattr(cr, "radius", "0dp"))
            return {
                "fill": fill,
                "stroke": stroke_c,
                "sw": stroke_w,
                "radius": radius,
                "oval": sattr(shape_el, "shape") == "oval",
                "layer": tag == "layer-list",
            }
    return None


_shapes = {}


def shape(name):
    if name not in _shapes:
        _shapes[name] = load_shape(name)
    return _shapes[name]


def dp(v, default=0.0):
    if v is None:
        return default
    v = str(v).strip()
    m = re.match(r"^(-?[\d.]+)(dp|dip|sp|px)?$", v)
    if m:
        n = float(m.group(1))
        return n
    return default


# ───────────────────────── 极简视图树 ─────────────────────────

class Node:
    __slots__ = ("tag", "a", "kids", "x", "y", "w", "h", "lines", "fnt", "line_h",
                 "ascent", "text", "tcolor", "tsize", "tsize_base", "id", "bold", "spacing",
                 "gravity", "lg", "max_lines", "ellipsize", "overflow", "path", "pad", "mar", "twidth_px")

    def __init__(self, el, path):
        self.tag = el.tag
        self.a = {aname(k): v for k, v in el.attrib.items()}
        self.kids = [Node(k, path + "/" + k.tag) for k in el if isinstance(k.tag, str)]
        self.x = self.y = self.w = self.h = 0.0
        self.lines = []
        self.fnt = None
        self.line_h = 0.0
        self.ascent = 0.0
        self.id = (self.a.get("id", "") or "").replace("@+id/", "").replace("@id/", "")
        self.text = ""
        self.tcolor = "#000000"
        self.tsize = 14.0
        self.tsize_base = 14.0
        self.bold = False
        self.spacing = 0.0
        self.gravity = ""
        self.max_lines = 0
        self.ellipsize = False
        self.overflow = 0.0
        self.path = path
        self._prep()

    # ---- 属性预处理 ----
    def _prep(self):
        a = self.a
        self.pad = (dp(a.get("paddingStart", a.get("paddingLeft"))),
                    dp(a.get("paddingTop")),
                    dp(a.get("paddingEnd", a.get("paddingRight"))),
                    dp(a.get("paddingBottom")))
        if "padding" in a:
            v = dp(a["padding"])
            self.pad = (v, v, v, v)
        ph = dp(a.get("paddingHorizontal"))
        pv = dp(a.get("paddingVertical"))
        if ph:
            self.pad = (ph, self.pad[1], ph, self.pad[3])
        if pv:
            self.pad = (self.pad[0], pv, self.pad[2], pv)
        self.mar = (dp(a.get("layout_marginStart", a.get("layout_marginLeft"))),
                    dp(a.get("layout_marginTop")),
                    dp(a.get("layout_marginEnd", a.get("layout_marginRight"))),
                    dp(a.get("layout_marginBottom")))
        if "layout_margin" in a:
            v = dp(a["layout_margin"])
            self.mar = (v, v, v, v)
        mh = dp(a.get("layout_marginHorizontal"))
        mv = dp(a.get("layout_marginVertical"))
        if mh:
            self.mar = (mh, self.mar[1], mh, self.mar[3])
        if mv:
            self.mar = (self.mar[0], mv, self.mar[2], mv)
        self.gravity = (a.get("gravity") or "").lower()
        self.lg = (a.get("layout_gravity") or "").lower()
        if self.tag == "TextView" or self.tag == "Button":
            raw = a.get("text", "")
            self.text = raw.replace("\\n", "\n")
            self.tsize = dp(a.get("textSize", "14sp"), 14.0)
            self.tsize_base = self.tsize
            self.tcolor = parse_color(a.get("textColor"), "#000000")
            self.bold = a.get("textStyle") == "bold"
            self.spacing = float(a.get("letterSpacing", "0") or 0)
            self.max_lines = int(float(a.get("maxLines", "0") or 0))
            self.ellipsize = bool(a.get("ellipsize"))


def parse_layout(path):
    root = ET.parse(path).getroot()
    return Node(root, os.path.basename(path))


def parse_info(name):
    p = os.path.join(RES, "xml", name + "_info.xml")
    root = ET.parse(p).getroot()

    def ga(key, dflt):
        for k, v in root.attrib.items():
            if k.split("}")[-1].split(":")[-1] == key:
                return v
        return dflt

    return int(ga("targetCellWidth", "2")), int(ga("targetCellHeight", "2"))


def declared_min(name):
    """info 里声明的 minWidth/minHeight（dp）—— 真机上格子缩到最小也到不了比这更小，
    所以这才是「可达的最小尺寸」。用来判断格子档位的超框到底会不会真的发生。"""
    p = os.path.join(RES, "xml", name + "_info.xml")
    root = ET.parse(p).getroot()

    def ga(key):
        for k, v in root.attrib.items():
            if k.split("}")[-1].split(":")[-1] == key:
                return v
        return None

    mw, mh = ga("minWidth"), ga("minHeight")
    w = int(re.sub(r"[^0-9]", "", mw)) if mw else None
    h = int(re.sub(r"[^0-9]", "", mh)) if mh else None
    return w, h


# ───────────────────────── 文本度量 ─────────────────────────

def char_adv(ch, f, size, spacing):
    return f.getlength(ch) + spacing * size


# 避头尾（禁则）：这些字符不能出现在行首，设备上 ICU 断行会把它们拉回上一行
NO_LINE_START = "。，、；：！？）」』】》”’…—·%"


def rebalance(lines, f, size, spacing, max_w):
    """模拟 Android textBreakStrategy="balanced"：把行宽拉平，避免孤字行"""
    if len(lines) < 2:
        return lines
    text = "".join(lines)
    n, k = len(text), len(lines)
    per = -(-n // k)                      # ceil
    out, i = [], 0
    while i < n:
        take = min(per, n - i)
        # 别让标点起行
        while take > 1 and i + take < n and text[i + take] in NO_LINE_START:
            take -= 1
        out.append(text[i:i + take])
        i += take
    if len(out) != k:
        return lines
    if any(sum(char_adv(c, f, size, spacing) for c in ln) > max_w + 0.5 for ln in out):
        return lines
    return out


def wrap_text(text, f, size, spacing, max_w):
    """按字符断行（CJK 友好 + 避头尾），返回行列表"""
    if max_w <= 0:
        max_w = 1e9
    out = []
    for para in text.split("\n"):
        if not para:
            out.append("")
            continue
        cur, cur_w = "", 0.0
        for ch in para:
            adv = char_adv(ch, f, size, spacing)
            if cur and cur_w + adv > max_w + 0.01:
                if ch in NO_LINE_START:
                    # 行首禁则：标点跟着上一行走（宁可轻微超宽，也不让「。」单独起行）
                    cur += ch
                    cur_w += adv
                    out.append(cur)
                    cur, cur_w = "", 0.0
                    continue
                out.append(cur)
                cur, cur_w = ch, adv
            else:
                cur += ch
                cur_w += adv
        if cur or not out:
            out.append(cur)
    return out


def ellipsize(line, f, size, spacing, max_w):
    if not line:
        return line
    dot = "…"
    while line and (sum(char_adv(c, f, size, spacing) for c in line) +
                    char_adv(dot, f, size, spacing)) > max_w:
        line = line[:-1]
    return line + dot


DIAG = []

# 与 XmWidgets.kt 的 fitScale 对应：正方形卡按实例边长等比收放字号
FIT = {
    "w_dc_days": (32.0, 148.0), "w_dc_unit_l": (12.0, 148.0), "w_dc_unit_r": (12.0, 148.0),
    "w_dc_cap": (11.0, 148.0), "w_mc_days": (32.0, 148.0), "w_mc_unit": (12.0, 148.0),
    "w_mc_pill": (11.0, 148.0), "w_qs_mark": (20.0, 148.0),
    "w_cb_days": (14.0, 222.0), "w_cb_quote": (12.0, 222.0),
    # 情话小卡正文、4×2 情话、两枚 4×1 卡也随边长缩放
    "w_qs_text": (12.0, 148.0), "w_qw_text": (14.0, 148.0), "w_qw_count": (11.0, 148.0),
    "w_rain_title": (12.0, 74.0), "w_rain_sub": (11.0, 74.0), "w_qm_pill": (12.0, 74.0),
    # 心情卡：名字 / 副文案 / 五枚翻页按钮在 moodViews 里同样走 fitScale(side, 148f)
    "w_mood_name": (16.0, 148.0), "w_mood_sub": (11.0, 148.0),
    "w_mood0": (12.0, 148.0), "w_mood1": (12.0, 148.0), "w_mood2": (12.0, 148.0),
    "w_mood3": (12.0, 148.0), "w_mood4": (12.0, 148.0),
}
CUR_SIDE = 0.0

# 与 XmWidgets.moodViews 里的 artSide 对应：插画边长 = 实例边长 × 0.34
ART_FIT = {"w_mood_art": 0.30}
ART_FIT_COMPACT = {"w_mood_art": 0.24}

# 与 moodViews 的 compact 分支对应：小卡隐藏副文案
HIDE_BELOW = {
    "w_mood_sub": 130.0, "w_dc_unit_l": 130.0, "w_qs_mark": 120.0, "w_cb_refresh": 175.0,
}

# 与 qsViews 的 setBreakStrategy(BALANCED) 对应：窄栏情话卡用平衡断行
BALANCED_IDS = {"w_qs_text"}

# 与 cbViews 的 tileH 对应：入口条高度 = 边长 × 0.19，夹在 34~52dp
TILE_FIT = {"w_cb_tiles": (0.19, 34.0, 52.0)}


def fit_size(node):
    """返回按实例边长缩放后的 sp（未在 FIT 表中则原样）"""
    base_ref = FIT.get(node.id)
    if not base_ref or CUR_SIDE <= 0:
        return node.tsize_base
    base, ref = base_ref
    k = max(0.72, min(1.15, CUR_SIDE / ref))
    return base * k


def note(level, node, msg):
    DIAG.append((level, node.path, msg))


def measure_text(node, avail_w_dp):
    a = node.a
    node.tsize = fit_size(node)
    if node.id == "w_qs_text":
        node.max_lines = 3 if CUR_SIDE >= 140 else 2
    if node.id == "w_cb_quote":
        node.max_lines = 3 if CUR_SIDE >= 200 else (2 if CUR_SIDE >= 170 else 1)
    if node.id == "w_qw_text":
        node.max_lines = 3 if CUR_SIDE >= 190 else 2
    size = node.tsize
    f = font(size * SCALE, node.bold)
    spacing = node.spacing
    max_w = avail_w_dp * SCALE - (node.pad[0] + node.pad[2]) * SCALE
    lines = wrap_text(node.text, f, size * SCALE, spacing, max_w) if node.text else [""]
    if node.id in BALANCED_IDS:
        lines = rebalance(lines, f, size * SCALE, spacing, max_w)
    line_extra = dp(a.get("lineSpacingExtra")) * SCALE
    if node.max_lines and len(lines) > node.max_lines:
        lines = lines[:node.max_lines]
        lines[-1] = ellipsize(lines[-1], f, size * SCALE, spacing, max_w)
        # 带 ellipsize="end" 的截断是设计本身（情话长度不可控，必须省略），不算排版事故；
        # 只有「没写 ellipsize、被 maxLines 硬切」才算问题
        if not node.ellipsize:
            note("clip", node, "文字超过 maxLines=%d 且没有 ellipsize，被硬切" % node.max_lines)
            node.overflow = 1
    asc, desc = f.getmetrics()
    node.line_h = (asc + desc) + line_extra
    node.ascent = asc
    node.fnt = f
    node.lines = lines
    node.twidth_px = max((sum(char_adv(c, f, size * SCALE, spacing) for c in ln)
                          for ln in lines), default=0.0)


# ───────────────────────── 测量 / 布局 ─────────────────────────

def resolve_mode(node, mode_key):
    v = node.a.get(mode_key, "wrap_content")
    if v == "match_parent":
        return ("match", 0.0)
    if v == "wrap_content":
        return ("wrap", 0.0)
    return ("exact", dp(v))


def weight_of(node):
    try:
        return float(node.a.get("layout_weight", "0") or 0)
    except ValueError:
        return 0.0


def measure(node, aw, ah):
    """aw/ah: 父容器给的可用宽高（dp）。返回 node.w/h（dp，含自身 padding，不含 margin）"""
    wm, wv = resolve_mode(node, "layout_width")
    hm, hv = resolve_mode(node, "layout_height")
    # 先定宽度
    if wm == "exact":
        w = wv
    elif wm == "match":
        w = aw
    else:
        w = None
    if hm == "exact":
        h = hv
    elif hm == "match":
        h = ah
    else:
        h = None

    hide_below = HIDE_BELOW.get(node.id)
    if hide_below is not None and CUR_SIDE > 0 and CUR_SIDE < hide_below:
        node.w, node.h = 0.0, 0.0
        node.lines = []
        node.text = ""
        return

    if node.tag in ("TextView", "Button"):
        measure_text(node, w if w is not None else aw)
        need_w = node.twidth_px / SCALE + node.pad[0] + node.pad[2]
        need_h = len(node.lines) * node.line_h / SCALE + node.pad[1] + node.pad[3]
        w = need_w if w is None else w
        h = need_h if h is None else h
        node.w, node.h = w, h
        if h + 0.5 < need_h:
            if h > 6:
                note("overflow", node, "文本高 %.1fdp > 可用 %.1fdp（%d 行）" % (need_h, h, len(node.lines)))
        if w + 0.5 < need_w:
            note("overflow", node, "文本宽 %.1fdp > 可用 %.1fdp" % (need_w, w))
        return

    if node.tag == "ImageView":
        w = dp(node.a.get("layout_width", "0dp")) if w is None else w
        h = dp(node.a.get("layout_height", "0dp")) if h is None else h
        factor = ART_FIT.get(node.id)
        if node.id == "w_mood_art" and CUR_SIDE < 130:
            factor = ART_FIT_COMPACT.get(node.id)
        if factor and CUR_SIDE > 0:
            w = h = CUR_SIDE * factor
        node.w, node.h = w, h
        return

    # 容器：先按已知框测量子节点
    box_w = w if w is not None else aw
    box_h = h if h is not None else ah
    cw, ch = layout_children(node, box_w, box_h)
    if w is None:
        w = cw + node.pad[0] + node.pad[2]
    if h is None:
        h = ch + node.pad[1] + node.pad[3]
    node.w, node.h = w, h
    # 宽度已知时子节点需按最终宽度重排
    if wm == "exact" or wm == "match":
        layout_children(node, w, h if h is not None else box_h)


def layout_children(node, box_w, box_h):
    """在 node 内部按 padding 排布子节点，返回内容所需 (w,h)（不含 padding）"""
    pl, pt, pr, pb = node.pad
    inner_w = max(0.0, box_w - pl - pr)
    inner_h = max(0.0, box_h - pt - pb)

    if node.tag == "FrameLayout":
        need_h = 0.0
        for k in node.kids:
            measure(k, inner_w, inner_h)
            need_h = max(need_h, k.h + k.mar[1] + k.mar[3])
            g = k.gravity
            x = k.mar[0]
            if gflag(g, "center") or gflag(g, "center_horizontal"):
                x = (inner_w - k.w) / 2 + (k.mar[0] - k.mar[2]) / 2
            elif gflag(g, "end") or gflag(g, "right"):
                x = inner_w - k.w - k.mar[2]
            y = k.mar[1]
            if gflag(g, "center") or gflag(g, "center_vertical"):
                y = (inner_h - k.h) / 2 + (k.mar[1] - k.mar[3]) / 2
            elif gflag(g, "bottom"):
                y = inner_h - k.h - k.mar[3]
            k.x, k.y = pl + x, pt + y
        return inner_w, need_h

    if node.tag == "RelativeLayout":
        # 只支持本工程用到的「两端对齐 + 垂直居中」两列模式
        need = 0.0
        for k in node.kids:
            measure(k, inner_w, inner_h)
            need = max(need, k.h)
            end = k.a.get("layout_alignParentEnd") == "true"
            x = inner_w - k.w if end else 0.0
            y = (inner_h - k.h) / 2 if k.a.get("layout_centerVertical") == "true" else 0.0
            k.x, k.y = pl + x, pt + max(0.0, y)
        return inner_w, need

    # LinearLayout
    horizontal = (node.a.get("orientation", "vertical") == "horizontal")
    gaps = ("layout_marginStart", "layout_marginTop", "layout_marginEnd", "layout_marginBottom")
    if horizontal:
        fixed = 0.0
        weights = 0.0
        for k in node.kids:
            if weight_of(k) > 0 and resolve_mode(k, "layout_width")[0] == "exact" \
                    and dp(k.a.get("layout_width")) == 0:
                weights += weight_of(k)
                fixed += k.mar[0] + k.mar[2]
                continue
            measure(k, inner_w, inner_h)
            fixed += k.w + k.mar[0] + k.mar[2]
        remain = max(0.0, inner_w - fixed)
        if weights > 0 and remain <= 0.01 and node.kids:
            note("overflow", node, "横向权重空间不足（固定内容已占满 %.1fdp）" % fixed)
        for k in node.kids:
            if weight_of(k) > 0 and resolve_mode(k, "layout_width")[0] == "exact" \
                    and dp(k.a.get("layout_width")) == 0:
                k.w = remain * weight_of(k) / weights if weights else 0.0
                hmode = resolve_mode(k, "layout_height")[0]
                if hmode == "match":
                    k.h = inner_h
                    force_measure(k, k.w, k.h)
                elif hmode == "exact":
                    k.h = dp(k.a.get("layout_height"))
                    force_measure(k, k.w, k.h)
                else:
                    size_by_content(k, k.w)
        total_w = 0.0
        for k in node.kids:
            total_w += k.w + k.mar[0] + k.mar[2]
        # 横向 gravity
        start = 0.0
        g = node.gravity
        if "center" in g:
            start = (inner_w - total_w) / 2
        elif gflag(g, "end") or gflag(g, "right"):
            start = inner_w - total_w
        cur = start
        max_h = 0.0
        for k in node.kids:
            cur += k.mar[0]
            ky = k.mar[1]
            if gflag(g, "center") or gflag(g, "center_vertical"):
                ky = (inner_h - k.h) / 2 + (k.mar[1] - k.mar[3]) / 2
            elif gflag(g, "bottom"):
                ky = inner_h - k.h - k.mar[3]
            k.x, k.y = pl + cur, pt + ky
            cur += k.w + k.mar[2]
            max_h = max(max_h, k.h + k.mar[1] + k.mar[3])
        if total_w > inner_w + 0.5:
            note("overflow", node, "横向内容宽 %.1fdp > 可用 %.1fdp" % (total_w, inner_w))
        return total_w, max_h

    # vertical
    fixed = 0.0
    weights = 0.0
    for k in node.kids:
        if weight_of(k) > 0 and resolve_mode(k, "layout_height")[0] == "exact" \
                and dp(k.a.get("layout_height")) == 0:
            weights += weight_of(k)
            fixed += k.mar[1] + k.mar[3]
            continue
        measure(k, inner_w, inner_h)
        fixed += k.h + k.mar[1] + k.mar[3]
    remain = max(0.0, inner_h - fixed)
    if weights > 0 and remain <= 0.01 and node.kids:
        note("overflow", node, "纵向权重空间不足（固定内容已占 %.1fdp / 可用 %.1fdp）" % (fixed, inner_h))
    for k in node.kids:
        if weight_of(k) > 0 and resolve_mode(k, "layout_height")[0] == "exact" \
                and dp(k.a.get("layout_height")) == 0:
            k.h = remain * weight_of(k) / weights if weights else 0.0
            if resolve_mode(k, "layout_width")[0] == "match":
                k.w = inner_w
            force_measure(k, k.w if k.w else inner_w, k.h)
    total_h = sum(k.h + k.mar[1] + k.mar[3] for k in node.kids)
    start = 0.0
    g = node.gravity
    if "center" in g:
        start = (inner_h - total_h) / 2
    elif gflag(g, "bottom"):
        start = inner_h - total_h
    if total_h > inner_h + 0.5:
        note("overflow", node, "纵向内容高 %.1fdp > 可用 %.1fdp" % (total_h, inner_h))
    cur = start
    for k in node.kids:
        cur += k.mar[1]
        gx = k.mar[0]
        if gflag(g, "center") or gflag(g, "center_horizontal"):
            gx = (inner_w - k.w) / 2 + (k.mar[0] - k.mar[2]) / 2
        elif gflag(g, "end") or gflag(g, "right"):
            gx = inner_w - k.w - k.mar[2]
        k.x, k.y = pl + gx, pt + cur
        cur += k.h + k.mar[3]
    total_w = max((k.w + k.mar[0] + k.mar[2] for k in node.kids), default=0.0)
    return total_w, total_h


def size_by_content(node, w):
    """已知宽度、按内容定高度（权重子节点常用）"""
    node.w = w
    if node.tag in ("TextView", "Button"):
        measure_text(node, w)
        node.h = len(node.lines) * node.line_h / SCALE + node.pad[1] + node.pad[3]
    else:
        _, ch = layout_children(node, w, 1e6)
        node.h = ch + node.pad[1] + node.pad[3]


def force_measure(node, w, h):
    """权重子节点在尺寸定下来之后补测内容（文字行、内部子节点）"""
    node.w, node.h = w, h
    if node.tag in ("TextView", "Button"):
        measure_text(node, w)
        need_h = len(node.lines) * node.line_h / SCALE + node.pad[1] + node.pad[3]
        if need_h > h + 0.5 and h > 6 and node.text.strip():
            note("clip", node, "文本需要 %.1fdp 高，实际只给 %.1fdp（%d 行）" % (need_h, h, len(node.lines)))
    elif node.tag == "ImageView":
        return
    else:
        layout_children(node, w, h)


def place(node):
    """按已测得的 w/h 计算子节点的绝对坐标（相对画布），递归向下"""
    pl, pt, pr, pb = node.pad
    iw = max(0.0, node.w - pl - pr)
    ih = max(0.0, node.h - pt - pb)

    if node.tag == "FrameLayout":
        for k in node.kids:
            g = k.gravity
            x, y = k.mar[0], k.mar[1]
            if gflag(g, "center") or gflag(g, "center_horizontal"):
                x = (iw - k.w) / 2 + (k.mar[0] - k.mar[2]) / 2
            elif gflag(g, "end") or gflag(g, "right"):
                x = iw - k.w - k.mar[2]
            if gflag(g, "center") or gflag(g, "center_vertical"):
                y = (ih - k.h) / 2 + (k.mar[1] - k.mar[3]) / 2
            elif gflag(g, "bottom"):
                y = ih - k.h - k.mar[3]
            k.x, k.y = node.x + pl + x, node.y + pt + y
            place(k)
        return

    if node.tag == "RelativeLayout":
        for k in node.kids:
            end = k.a.get("layout_alignParentEnd") == "true"
            x = iw - k.w if end else 0.0
            y = (ih - k.h) / 2 if k.a.get("layout_centerVertical") == "true" else 0.0
            k.x, k.y = node.x + pl + x, node.y + pt + max(0.0, y)
            place(k)
        return

    horizontal = (node.a.get("orientation", "vertical") == "horizontal")
    g = node.gravity
    if horizontal:
        total = sum(k.w + k.mar[0] + k.mar[2] for k in node.kids)
        start = 0.0
        if "center" in g:
            start = (iw - total) / 2
        elif gflag(g, "end") or gflag(g, "right"):
            start = iw - total
        cur = start
        for k in node.kids:
            cur += k.mar[0]
            kg = k.lg or g
            ky = k.mar[1]
            if gflag(kg, "center") or gflag(kg, "center_vertical"):
                ky = (ih - k.h) / 2 + (k.mar[1] - k.mar[3]) / 2
            elif gflag(kg, "bottom"):
                ky = ih - k.h - k.mar[3]
            k.x, k.y = node.x + pl + cur, node.y + pt + ky
            cur += k.w + k.mar[2]
            place(k)

        # LinearLayout 横向默认 baselineAligned=true：按第一行基线对齐
        if node.a.get("baselineAligned", "true") != "false":
            bases = []
            for k in node.kids:
                if k.tag in ("TextView", "Button") and k.lines:
                    bases.append((k, k.y + k.pad[1] + k.ascent / SCALE))
            if len(bases) > 1:
                target = max(b for _, b in bases)
                for k, b in bases:
                    k.y += target - b
                    place(k)
        return

    total = sum(k.h + k.mar[1] + k.mar[3] for k in node.kids)
    start = 0.0
    if gflag(g, "center") or gflag(g, "center_vertical"):
        start = (ih - total) / 2
    elif gflag(g, "bottom"):
        start = ih - total
    cur = start
    for k in node.kids:
        cur += k.mar[1]
        kg = k.lg or g
        gx = k.mar[0]
        if gflag(kg, "center") or gflag(kg, "center_horizontal"):
            gx = (iw - k.w) / 2 + (k.mar[0] - k.mar[2]) / 2
        elif gflag(kg, "end") or gflag(kg, "right"):
            gx = iw - k.w - k.mar[2]
        k.x, k.y = node.x + pl + gx, node.y + pt + cur
        cur += k.h + k.mar[3]
        place(k)


# ───────────────────────── 绘制 ─────────────────────────

_bmps = {}


def bitmap(name):
    """drawable-nodpi 里的位图卡面（v1.6.2 的纸纹底）"""
    if name in _bmps:
        return _bmps[name]
    for ext in (".webp", ".png"):
        p = os.path.join(RES, "drawable-nodpi", name + ext)
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            _bmps[name] = im
            return im
    _bmps[name] = None
    return None


# 系统 widget 自己的圆角（实测这台机器 ≈10dp）。真机上内容会被裁到这个圆角上，
# 而位图里烘的是更小的兜底圆角（3.5%），所以实际看到的圆角 = 系统圆角。
SYS_RADIUS_DP = 10.0


def draw_background(d, node, name):
    """优先用位图卡面，其次 XML shape"""
    bmp = bitmap(name)
    w = max(1, int(round(node.w * SCALE)))
    h = max(1, int(round(node.h * SCALE)))
    if bmp is not None:
        img = bmp.resize((w, h), Image.LANCZOS)
        # 系统裁切：半径取「兜底圆角」与系统圆角的较大者（系统只会裁得更圆）
        r = max(min(w, h) * 0.035, SYS_RADIUS_DP * SCALE)
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], r, fill=255)
        alpha = Image.composite(img.split()[3], Image.new("L", (w, h), 0), mask)
        img.putalpha(alpha)
        canvas = getattr(d, "_image", None) or getattr(d, "im", None)
        canvas.paste(img, (int(node.x * SCALE), int(node.y * SCALE)), img)
        return
    draw_shape(d, node, name)


def draw_shape(d, node, name):
    sh = shape(name) if name else None
    x0, y0 = node.x * SCALE, node.y * SCALE
    x1, y1 = (node.x + node.w) * SCALE, (node.y + node.h) * SCALE
    if sh is None or sh.get("vector"):
        return
    fill = sh.get("fill")
    stroke = sh.get("stroke")
    sw = max(0, int(round(sh.get("sw", 0) * SCALE)))
    if sh.get("oval"):
        if fill:
            d.ellipse([x0, y0, x1 - 1, y1 - 1], fill=fill, outline=stroke, width=max(1, sw) if stroke else 0)
        return
    r = sh.get("radius", 0) * SCALE
    if fill or (stroke and sw):
        d.rounded_rectangle([x0, y0, x1 - 1, y1 - 1], radius=r, fill=fill,
                            outline=stroke, width=max(1, sw) if stroke else 0)


def draw_node(d, node):
    bg = node.a.get("background")
    m = re.match(r"@drawable/(\w+)", bg or "")
    if m:
        draw_background(d, node, m.group(1))
    elif bg:
        # @color/xxx 或 #RRGGBB 的纯色底（如 3×3 引文左侧那道 2dp 竖线）
        c = parse_color(bg)
        if c:
            d.rounded_rectangle([node.x * SCALE, node.y * SCALE,
                                 (node.x + node.w) * SCALE - 1, (node.y + node.h) * SCALE - 1], fill=c)

    if node.tag in ("TextView", "Button") and node.text:
        f = node.fnt
        size_px = node.tsize * SCALE
        x_left = (node.x + node.pad[0]) * SCALE
        box_w = (node.w - node.pad[0] - node.pad[2]) * SCALE
        block_h = len(node.lines) * node.line_h
        box_h = (node.h - node.pad[1] - node.pad[3]) * SCALE
        top = (node.y + node.pad[1]) * SCALE
        g = node.gravity
        if gflag(g, "center") or gflag(g, "center_vertical"):
            top += (box_h - block_h) / 2
        elif gflag(g, "bottom"):
            top += box_h - block_h
        yy = top
        for ln in node.lines:
            lw = sum(char_adv(c, f, size_px, node.spacing) for c in ln)
            xx = x_left
            if gflag(g, "center") or gflag(g, "center_horizontal"):
                xx = x_left + (box_w - lw) / 2
            elif gflag(g, "end") or gflag(g, "right"):
                xx = x_left + box_w - lw
            for ch in ln:
                d.text((xx, yy + node.ascent), ch, font=f, fill=node.tcolor, anchor="ls")
                xx += char_adv(ch, f, size_px, node.spacing)
            yy += node.line_h

    if node.tag == "ImageView":
        m = re.match(r"@drawable/(\w+)", node.a.get("src") or "")
        if m:
            bmp = bitmap(m.group(1))
            if bmp is not None:
                # 等比缩放（fitCenter）后居中贴
                bw, bh = bmp.size
                scale = min(node.w * SCALE / bw, node.h * SCALE / bh)
                tw, th = max(1, int(bw * scale)), max(1, int(bh * scale))
                img = bmp.resize((tw, th), Image.LANCZOS)
                cx = int((node.x + node.w / 2) * SCALE - tw / 2)
                cy = int((node.y + node.h / 2) * SCALE - th / 2)
                canvas = getattr(d, "_image", None) or getattr(d, "im", None)
                canvas.paste(img, (cx, cy), img)

    for k in node.kids:
        draw_node(d, k)


# ───────────────────────── 主流程 ─────────────────────────

def render(name, cell, scale, size_dp=None):
    global SCALE
    SCALE = scale
    cols, rows = parse_info(name)
    if size_dp is not None:
        w_dp, h_dp = size_dp
    else:
        w_dp, h_dp = cols * cell, rows * cell
    global CUR_SIDE
    CUR_SIDE = min(w_dp, h_dp)
    root = parse_layout(os.path.join(RES, "layout", name + ".xml"))
    root.x = root.y = 0.0
    measure(root, w_dp, h_dp)
    root.x = root.y = 0.0
    root.w, root.h = w_dp, h_dp
    place(root)
    img = Image.new("RGBA", (int(w_dp * scale), int(h_dp * scale)), (238, 236, 240, 255))
    d = ImageDraw.Draw(img)
    root.x = root.y = 0.0
    draw_node(d, root)
    return img, (w_dp, h_dp)


def main():
    only = None
    scale = SCALE
    args = sys.argv[1:]
    for i, a in enumerate(args):
        if a == "--only":
            only = args[i + 1]
        if a == "--scale":
            scale = float(args[i + 1])
    os.makedirs(OUT, exist_ok=True)
    names = sorted(f[:-4] for f in os.listdir(os.path.join(RES, "layout"))
                   if f.startswith("widget_") and f.endswith(".xml"))
    if only:
        names = [n for n in names if only in n]

    strips = []
    for name in names:
        imgs = []
        for pname, cell in PROFILES:
            DIAG.clear()
            img, size = render(name, cell, scale)
            img.save(os.path.join(OUT, "%s_%s.png" % (name, pname)))
            print("\n== %s · %s：%.0f×%.0f dp（格子 %.0fdp）" % (name, pname, size[0], size[1], cell))
            seen = []
            for lvl, path, msg in DIAG:
                if (lvl, path, msg) in seen:
                    continue
                seen.append((lvl, path, msg))
            if seen:
                for lvl, path, msg in seen:
                    print("   [%s] %s %s" % (lvl, path, msg))
            else:
                print("   ok")
            imgs.append((pname, img))
        # 声明最小尺寸档：真机格子缩到最小也到不了比 info 里 minWidth/minHeight 更小，
        # 这一档才是「一定会遇到」的下限。上面 min55 只是参考档（部分卡比声明值还小）。
        dm = declared_min(name)
        if dm[0] and dm[1]:
            DIAG.clear()
            dimg, size = render(name, 55, scale, size_dp=dm)
            dimg.save(os.path.join(OUT, "%s_declared.png" % name))
            seen = []
            for lvl, path, msg in DIAG:
                if (lvl, path, msg) in seen:
                    continue
                seen.append((lvl, path, msg))
            tag = "可达下限（声明 %.0f×%.0fdp）" % dm
            print("\n== %s · %s" % (name, tag))
            if seen:
                for lvl, path, msg in seen:
                    print("   [%s] %s %s" % (lvl, path, msg))
            else:
                print("   ok")
        # 三档横排成一张对比图
        pad = int(12 * scale)
        W = sum(i.width for _, i in imgs) + pad * (len(imgs) + 1)
        H = max(i.height for _, i in imgs) + pad * 2
        strip = Image.new("RGBA", (W, H), (250, 249, 247, 255))
        x = pad
        for pname, i in imgs:
            strip.paste(i, (x, pad))
            x += i.width + pad
        strip.save(os.path.join(OUT, name + "_all.png"))
        strips.append(name)

    print("\n输出目录：%s" % OUT)
    print("单张：<widget>_min55 / _phone74 / _big92.png；三档对比：<widget>_all.png")


if __name__ == "__main__":
    main()
