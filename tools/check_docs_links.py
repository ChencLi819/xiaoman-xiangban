#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查仓库内 markdown 的相对链接与图片是否指向真实存在的文件。

用法: python3 tools/check_docs_links.py [起始目录]

只校验相对路径（跳过 http(s)://、mailto:、纯 #锚点），
锚点片段会被剥离后再检查文件是否存在。
"""
import os
import re
import sys

LINK_RE = re.compile(r'!?\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)')
SKIP_PREFIX = ('http://', 'https://', 'mailto:', 'data:', '#')


def main(root):
    bad = []
    checked = 0
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames
                       if d not in ('.git', 'build', '.gradle', '.kotlin', 'node_modules', '__pycache__')]
        for name in filenames:
            if not name.endswith(('.md', '.markdown')):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding='utf-8', errors='replace') as fh:
                text = fh.read()
            # 去掉围栏代码块：示例路径不该被当成链接
            text = re.sub(r'^```.*?^```', '', text, flags=re.S | re.M)
            for m in LINK_RE.finditer(text):
                target = m.group(1)
                if target.startswith(SKIP_PREFIX) or target.startswith('/'):
                    continue
                rel = target.split('#', 1)[0]
                if not rel:
                    continue
                checked += 1
                resolved = os.path.normpath(os.path.join(dirpath, rel))
                if not os.path.exists(resolved):
                    bad.append((os.path.relpath(path, root), target, rel))

    print('checked %d relative links under %s' % (checked, root or '.'))
    for src, target, rel in bad:
        print('BROKEN  %s -> %s (resolved: %s)' % (src, target, rel))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
