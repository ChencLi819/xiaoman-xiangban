# -*- coding: utf-8 -*-
"""桌面卡片底板 v4（v1.6.2）：AI 纸张底图真正用回卡面。

每张卡 = 纸张纹理（取自 AI 生成图中央干净纸面，镜像平铺无接缝）
        + 白色纱层（压淡纹理，保证文字对比度）
        + 双细线框（跟着卡片等比缩放）
        + 圆角（烘进 alpha，但半径刻意小于系统 widget 圆角，见 RADIUS_RATIO 注释）
        + 大卡另叠她/他 Q 版插画（同样压在纱下，做氛围而非主体）

产出：
  res/drawable-nodpi/w_board_{square,big,wide,slim2,plain}.webp —— 真正被 8 个布局引用的卡面
  res/drawable-nodpi/widget_preview_*.webp                      —— 「添加小工具」面板预览图（8 张）

用法：python tools/gen_widget_boards.py
"""
from PIL import Image, ImageDraw, ImageFont

import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "generated-images")
MOOD = os.path.join(ROOT, "docs", "prototype", "assets", "mood")
DST = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")

KAI = "C:/Windows/Fonts/simkai.ttf"
MSYH = "C:/Windows/Fonts/msyh.ttc"

# 文字与点缀色（必须与 res/values/widget_colors.xml 逐值一致，改色板请同步这里）
INK = (18, 16, 26)        # w_ink      #12101A
INK2 = (85, 80, 95)       # w_ink2     #55505F
INK3 = (92, 86, 102)      # w_ink3     #5C5666
ACCENT = (176, 47, 73)    # w_accent   #B02F49（全站唯一强调色）
ACCENT_SOFT = (247, 228, 225)  # w_accent_soft #F7E4E1
SOFT = (241, 230, 214)    # w_soft     #F1E6D6
HER = (124, 63, 57)       # w_her_ink  #7C3F39
HIM = (53, 87, 140)       # w_him_ink  #35578C
GOLD = (138, 94, 25)      # w_gold_ink #8A5E19
QINK = (74, 61, 89)       # w_q_ink    #4A3D59
PAPER = (255, 253, 248)
LINE = (214, 199, 188)

PERSON_ALPHA = 150   # 插画不透明度（白纱之上）：要看得见，又别抢字
# 圆角：**必须小于系统 widget 圆角**（Android 12+ 的 system_app_widget_background_radius
# 一般是 16dp，Pixel 系 28dp）。原因：系统会在 widget 下面画一层自己的圆角底色
# （浅色主题约 #F1F0F4），再把内容裁到系统圆角上。如果我们的圆角比系统大，
# 圆角外那圈透明区就会露出系统底色 —— 就是 v1.6.2 一版里四角那块"灰角"。
# 实测这台机器：系统 widget 圆角 ≈ 10dp（固定 dp，不随卡片变大）。而位图圆角是百分比，
# 卡片越大半径越大 —— 3×3 到 222dp 时 5.5% 就是 12dp，已经超过系统圆角，四角又露淡蓝。
# 所以压到 3.5%：110dp≈3.9 / 148≈5.2 / 184≈6.4 / 222≈7.8 / 276≈9.7，全部 ≤10dp。
RADIUS_RATIO = 0.035
FRAME_RATIO = 0.05   # 细线框内缩
VEIL_ALPHA = 138      # 白纱不透明度（0-255）：越大越淡、文字越清楚


# ── 纸张底 ────────────────────────────────────────────────
def load_paper():
    """从 AI 生成图里取一块无印章 / 无水印 / 无插画的干净纸面"""
    src = Image.open(os.path.join(GEN, "Warm_cream_ivory_card_backgrou_2026-10-24T09-02-53.png")).convert("RGB")
    return src.crop((200, 200, 560, 800))       # 360×600 干净纸面


PAPER_TEX = load_paper()


def paper_fill(w, h):
    """镜像平铺到目标尺寸：纹理密度不变，且没有明显接缝"""
    pw, ph = PAPER_TEX.size
    canvas = Image.new("RGB", (w, h))
    for ty in range(0, h, ph):
        for tx in range(0, w, pw):
            tile = PAPER_TEX
            if (tx // pw) % 2 == 1:
                tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
            if (ty // ph) % 2 == 1:
                tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
            canvas.paste(tile, (tx, ty))
    return canvas


def rounded_mask(w, h, r):
    """4 倍超采样再缩回来：圆角边缘没有锯齿（位图放大/缩小时也不出直角）"""
    ss = 4
    m = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * ss - 1, h * ss - 1], r * ss, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def frame(d, w, h, r):
    """双细线框：外粗内细。圆角半径按卡片圆角等比缩小（原来是 r-inset，
    圆角降到 5.5% 之后会算出接近 0 的值，线框四角变成直角、跟卡面圆角打架）"""
    inset = int(min(w, h) * FRAME_RATIO)
    lw = max(2, int(min(w, h) * 0.004))
    d.rounded_rectangle([inset, inset, w - 1 - inset, h - 1 - inset],
                        max(6, int(r * 0.62)), outline=LINE + (255,), width=lw)
    gap = max(3, int(min(w, h) * 0.016))
    inner = inset + gap
    d.rounded_rectangle([inner, inner, w - 1 - inner, h - 1 - inner],
                        max(4, int(r * 0.42)), outline=LINE + (150,), width=max(1, lw // 2))


def paste_person(im, person, h_ratio, left, at_top=False):
    """Q 版插画：贴在角落（贴边裁切），半透明压在白纱之上"""
    target_h = int(im.height * h_ratio)
    p = person.resize((int(person.width * target_h / person.height), target_h), Image.LANCZOS)
    a = p.split()[3].point(lambda v: int(v * PERSON_ALPHA / 255))
    if at_top:
        # 顶角落位时下半身会悬空：底部 35% 渐隐，像从纸里长出来
        fade_h = int(p.height * 0.35)
        grad = Image.new("L", (1, p.height), 255)
        for i in range(p.height - fade_h, p.height):
            k = (p.height - i) / fade_h
            grad.putpixel((0, i), int(255 * k))
        grad = grad.resize(p.size)
        a = Image.composite(a, Image.new("L", p.size, 0), grad)
    p.putalpha(a)
    # 站在「内框里」：不与细线框相交，也不会被卡片圆角切掉
    inset = int(min(im.size) * FRAME_RATIO) + max(2, int(min(im.size) * 0.012))
    x = inset if left else im.width - p.width - inset
    y = inset if at_top else im.height - p.height - inset
    im.alpha_composite(p, (x, y))


HER_IMG = Image.open(os.path.join(MOOD, "her-tian.png")).convert("RGBA")
HIM_IMG = Image.open(os.path.join(MOOD, "him-tian.png")).convert("RGBA")


def card(w, h, persons=None):
    """一张卡面：纸纹 → 插画 → 白纱 → 双线框 → 圆角 alpha"""
    r = int(min(w, h) * RADIUS_RATIO)
    im = paper_fill(w, h).convert("RGBA")

    im = Image.alpha_composite(im, Image.new("RGBA", (w, h), PAPER + (VEIL_ALPHA,)))

    # 2×2：底部两角各一枚，高 = 短边 16%（148dp 卡约 24dp）。
    # 两枚之间留出的中间地带 ≈ 卡宽 55%，居中文字（天数说明、日期胶囊）不会压到人。
    if persons == "bottom":
        ratio = 0.16
        paste_person(im, HER_IMG, ratio, True)
        paste_person(im, HIM_IMG, ratio, False)

    d = ImageDraw.Draw(im)
    frame(d, w, h, r)

    im.putalpha(rounded_mask(w, h, r))
    return im


def save(im, name):
    im.save(os.path.join(DST, name + ".webp"), quality=90, method=6)


# ── 卡面 ─────────────────────────────────────────────────
# persons：None = 不放插画；"bottom" = 底部两角各一枚（内容居中，四角留白）
# 3×3 顶部有顶栏、底部是四枚入口按钮；4×2 底部是天条按钮 —— 都没有安全角落，
# 硬塞人物只会被文字/按钮压住（上一版就是「按钮后面露半张脸」），故一律不放；
# 3×3 的插画改为布局内的两枚圆形头像（w_face_her / w_face_him，见下）。
SPECS = {
    "w_board_square": (1024, 1024, "bottom"),   # 2×2：人物在底部两角
    "w_board_big": (1024, 1024, None),          # 3×3：插画改内联头像
    "w_board_wide": (1536, 768, None),          # 4×2：内容占满，不放
    "w_board_plain": (1024, 1024, None),        # 2×2 无人物版（心情卡：页内已有插画）
    "w_board_slim2": (1536, 384, None),         # 4×1：同上
}
BOARDS = {}
for name, (w, h, persons) in SPECS.items():
    BOARDS[name] = card(w, h, persons)
    save(BOARDS[name], name)
print("卡面 %d 张 ok" % len(BOARDS))



# ── 内联头像（3×3 顶栏用）────────────────────────────────
def round_face(src, px=192):
    """裁头肩方图 → 缩到 px → 圆形 alpha；给 3×3 顶栏当头像用"""
    rgba = src.convert("RGBA")
    l, t, r, b = rgba.split()[3].getbbox()
    side = int((r - l) * 0.86)
    cx = (l + r) // 2
    x0 = max(0, cx - side // 2)
    y0 = max(0, t - int(side * 0.02))
    head = rgba.crop((x0, y0, min(rgba.width, x0 + side), min(rgba.height, y0 + side)))
    head = head.resize((px, px), Image.LANCZOS)

    ss = 4
    mask = Image.new("L", (px * ss, px * ss), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px * ss - 1, px * ss - 1], fill=255)
    mask = mask.resize((px, px), Image.LANCZOS)
    # 圆形之外留 6% 内边距，头像不贴边
    inner = head.split()[3]
    head.putalpha(Image.composite(inner, Image.new("L", (px, px), 0), mask))
    return head


for fname, src in (("w_face_her", HER_IMG), ("w_face_him", HIM_IMG)):
    round_face(src).save(os.path.join(DST, fname + ".webp"), quality=92, method=6)
print("头像 2 张 ok")


# ── 预览图（同款卡面 + 示例内容）────────────────────────────
def kai(s):
    return ImageFont.truetype(KAI, s)


def hei(s):
    return ImageFont.truetype(MSYH, s)


HALO = PAPER + (240,)


def txt(d, xy, text, font, fill, anchor="lm"):
    d.text(xy, text, font=font, fill=fill, anchor=anchor, stroke_width=2, stroke_fill=HALO)


def heart(d, cx, cy, r, col):
    d.ellipse([cx - r, cy - r, cx, cy + 2], fill=col + (255,))
    d.ellipse([cx, cy - r, cx + r, cy + 2], fill=col + (255,))
    d.polygon([(cx - r + 1, cy - 2), (cx + r - 1, cy - 2), (cx, cy + r + 2)], fill=col + (255,))


def preview(board, draw_fn, name):
    im = BOARDS[board].copy()
    draw_fn(ImageDraw.Draw(im))
    save(im, name)


# 2×2 在一起
def p_day(d):
    txt(d, (140, 380), "第", kai(28), INK2)
    txt(d, (200, 420), "1268", kai(100), ACCENT)
    txt(d, (580, 450), "天", kai(28), INK2)
    txt(d, (140, 570), "自 2023.03.14", kai(26), INK3)


# 2×2 见面倒计时
def p_meet(d):
    txt(d, (140, 330), "距下次见面", kai(26), INK2)
    txt(d, (140, 450), "23", kai(115), HIM)
    txt(d, (350, 480), "天", kai(28), INK2)
    d.rounded_rectangle([140, 580, 470, 660], 40, fill=(235, 242, 251, 255))
    txt(d, (165, 620), "11.15 · 成都", kai(26), HIM)


# 2×2 今日心情（翻页版：插画 + 名字 + 一句话 + 五枚翻页）
def p_mood(d):
    art = HER_IMG.copy()
    h = 360
    art = art.resize((int(art.width * h / art.height), h), Image.LANCZOS)
    im = d._image if hasattr(d, "_image") else d.im
    im.alpha_composite(art, (512 - art.width // 2, 170))
    txt(d, (512, 600), "甜", kai(56), INK, "mm")
    txt(d, (512, 680), "甜度超标，想贴贴", kai(28), INK2, "mm")
    labels = ["甜", "想", "累", "丧", "气"]
    x = 130
    dots = [(208, 138, 92), (201, 162, 39), (126, 147, 180), (139, 126, 155), (176, 47, 73)]
    for i, ch in enumerate(labels):
        if i == 0:      # 选中那档：accent 胶囊（识别点转白 + 白字）
            d.rounded_rectangle([x - 4, 748, x + 120, 842], 47, fill=ACCENT + (255,))
            d.ellipse([x + 50, 760, x + 66, 776], fill=(255, 255, 255, 255))
            d.text((x + 58, 810), ch, font=kai(28), fill=(255, 255, 255), anchor="mm")
        else:
            d.ellipse([x + 50, 760, x + 66, 776], fill=dots[i] + (255,))
            d.text((x + 58, 810), ch, font=kai(28), fill=INK2, anchor="mm")
        x += 152


# 2×2 情话小卡：顶栏「引号 …… 换一句」+ 正文居中（底部让给插画）
def p_quote_small(d):
    txt(d, (130, 150), "“", kai(60), ACCENT)
    txt(d, (890, 150), "换一句", kai(24), INK, "rm")
    txt(d, (130, 430), "我走过许多地方的路，行过", kai(33), INK)
    txt(d, (130, 495), "许多地方的桥，却只爱过", kai(33), INK)
    txt(d, (130, 560), "一个正当最好年龄的人。", kai(33), INK)


# 4×2 每日情话：顶栏「引号 …… 换一句」+ 正文 + 页脚计数（与 2×2 同构）
def p_quote_wide(d):
    txt(d, (120, 140), "“", kai(50), ACCENT)
    txt(d, (1410, 140), "换一句", kai(26), INK, "rm")
    txt(d, (120, 310), "我走过许多地方的路，行过许多地方的桥，", kai(44), INK)
    txt(d, (120, 385), "却只爱过一个正当最好年龄的人。", kai(44), INK)
    txt(d, (120, 580), "每日情话 · 109 条精选", hei(22), INK3)


# 4×1 快速记一笔
def p_quick(d):
    d.ellipse([110, 120, 250, 260], fill=SOFT + (255,))
    d.text((180, 190), "＋", font=kai(50), fill=ACCENT + (255,), anchor="mm")
    d.rounded_rectangle([290, 130, 1250, 250], 60, fill=PAPER + (240,))
    txt(d, (330, 190), "今天想记点什么…", kai(30), INK3)
    d.ellipse([1290, 130, 1430, 260], fill=SOFT + (255,))
    d.text((1360, 195), "→", font=kai(40), fill=ACCENT + (255,), anchor="mm")


# 4×1 雨过
def p_rain(d):
    d.ellipse([110, 120, 250, 260], fill=(243, 239, 247, 255))
    txt(d, (180, 190), "雨", kai(40), QINK, "mm")
    txt(d, (300, 160), "雨过 · 翻一篇", kai(40), INK)
    txt(d, (300, 230), "把不开心翻篇，继续晴", kai(26), INK2)


# 3×3 相伴大卡（顶栏：她头像 + 第 N 天 + 他头像；入口两字标签）
def p_combo(d):
    im = d._image
    for fname, cx in (("her", 250), ("him", 774)):
        face = Image.open(os.path.join(DST, "w_face_" + fname + ".webp")).convert("RGBA")
        face = face.resize((72, 72), Image.LANCZOS)
        im.alpha_composite(face, (cx - 36, 94))
    txt(d, (512, 130), "第 1268 天", kai(34), INK, "mm")
    d.rectangle([90, 226, 96, 500], fill=ACCENT + (255,))     # 2dp 引用竖线
    txt(d, (130, 300), "我走过许多地方的路，行过许多", kai(36), INK)
    txt(d, (130, 370), "地方的桥，却只爱过一个正当", kai(36), INK)
    txt(d, (130, 440), "最好年龄的人。", kai(36), INK)
    d.rounded_rectangle([128, 640, 420, 700], 30, fill=SOFT + (255,))
    txt(d, (152, 670), "距见面 23 天", kai(24), INK2)
    txt(d, (900, 670), "换一句", kai(24), ACCENT, "rm")
    labels = [("日记", ACCENT), ("心情", GOLD), ("情话", HER), ("雨过", QINK)]
    x = 90
    for t, col in labels:
        d.rounded_rectangle([x, 760, x + 196, 930], 32, fill=SOFT + (255,))
        txt(d, (x + 98, 845), t, kai(32), INK, "mm")
        txt(d, (x + 98, 800), "·", kai(30), col, "mm")   # 图标位：一小点语义色
        x += 216


preview("w_board_square", p_day, "widget_preview_day_counter")
preview("w_board_square", p_meet, "widget_preview_meet_countdown")
preview("w_board_plain", p_mood, "widget_preview_mood")
preview("w_board_square", p_quote_small, "widget_preview_quote_small")
preview("w_board_wide", p_quote_wide, "widget_preview_quote_wide")
preview("w_board_slim2", p_quick, "widget_preview_quick_memo")
preview("w_board_slim2", p_rain, "widget_preview_rain")
preview("w_board_big", p_combo, "widget_preview_combo")

print("预览 %d 张 ok" % len([f for f in __import__("os").listdir(DST) if f.startswith("widget_preview")]))
