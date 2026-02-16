# 保存为：save_versions.py
import sys
import subprocess
from datetime import datetime

# 关键包列表（你的项目实际用到的）
key_packages = [
    'Flask',
    'paddlepaddle',
    'paddleocr',
    'Pillow',
    'PyMuPDF',
    'paddlex',
    'numpy',
    'opencv-python',
    'opencv-contrib-python',
    'werkzeug',
    'jinja2'
]

print("正在检查包版本...")

# 获取版本
versions = []
for pkg in key_packages:
    try:
        # 使用pip show命令获取版本
        result = subprocess.run(
            f'pip show {pkg}',
            shell=True,
            capture_output=True,
            text=True
        )

        if result.returncode == 0:
            for line in result.stdout.split('\n'):
                if line.startswith('Version:'):
                    version = line.split('Version:')[1].strip()
                    versions.append(f"{pkg}=={version}")
                    print(f"✅ {pkg}: {version}")
                    break
        else:
            versions.append(f"# {pkg}: 未安装")
            print(f"❌ {pkg}: 未安装")

    except Exception as e:
        versions.append(f"# {pkg}: 检查失败")
        print(f"⚠️ {pkg}: 检查失败 - {e}")

# 保存到文件
with open('requirements_locked.txt', 'w', encoding='utf-8') as f:
    f.write(f"# BidGuard OCR Service - 版本锁定\n")
    f.write(f"# 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write(f"# Python: {sys.version.split()[0]}\n")
    f.write("#\n")
    f.write("# 注意：不要随意升级这些版本，OCR对版本敏感\n")
    f.write("#\n\n")

    for line in versions:
        f.write(line + "\n")

print(f"\n✅ 版本已保存到: requirements_locked.txt")
print(f"   共记录了 {len([v for v in versions if '==' in v])} 个包的版本")
