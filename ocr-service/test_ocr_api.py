# test_ocr_api.py
import io
import os
import time

import requests
from PIL import Image

# ========================================================================
# 🔧 OCR 服务器配置 - 通过注释切换服务器
# ========================================================================

# 🟢 PaddleOCR 服务器 (原版，35秒左右)
# OCR_BASE_URL = "http://localhost:5000"
# OCR_ENGINE_NAME = "PaddleOCR"

# 🔵 EasyOCR 服务器 (高速版，10-15秒)  
OCR_BASE_URL = "http://localhost:5001"
OCR_ENGINE_NAME = "EasyOCR"

# ========================================================================

DEFAULT_TEST_FILE = os.getenv('BIDGUARD_TEST_FILE', 'test.pdf')
IMAGE_COMPRESS_EXTS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp', '.tif', '.tiff'}

CLIENT_COMPRESS_DEFAULT = True
_ENV_COMPRESS = os.getenv('BIDGUARD_CLIENT_COMPRESS')
ENABLE_CLIENT_COMPRESS = CLIENT_COMPRESS_DEFAULT if _ENV_COMPRESS is None else _ENV_COMPRESS.lower() in {'1', 'true', 'yes', 'on'}


def _env_int(name, fallback):
    try:
        return int(os.getenv(name, fallback))
    except (TypeError, ValueError):
        return fallback


CLIENT_MAX_SIDE = _env_int('BIDGUARD_CLIENT_MAX_SIDE', 1000)
CLIENT_JPEG_QUALITY = _env_int('BIDGUARD_CLIENT_JPEG_QUALITY', 82)


def compress_image_for_upload(file_path, max_side=CLIENT_MAX_SIDE, quality=CLIENT_JPEG_QUALITY):
    """Resize + recompress locally before uploading to the OCR service."""
    with Image.open(file_path) as img:
        img = img.convert('RGB')
        orig_width, orig_height = img.size
        max_dim = max(orig_width, orig_height)
        new_width, new_height = orig_width, orig_height
        scale_ratio = 1.0
        if max_dim > max_side:
            scale_ratio = max_side / max_dim
            new_width = max(1, int(orig_width * scale_ratio))
            new_height = max(1, int(orig_height * scale_ratio))
            img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)

        buffer = io.BytesIO()
        img.save(buffer, format='JPEG', quality=quality, optimize=True)
        buffer.seek(0)

    orig_size = os.path.getsize(file_path)
    compressed_size = buffer.getbuffer().nbytes
    base_name = os.path.splitext(os.path.basename(file_path))[0]
    meta = {
        "filename": f"{base_name}_client.jpg",
        "content_type": "image/jpeg",
        "orig_width": orig_width,
        "orig_height": orig_height,
        "new_width": new_width,
        "new_height": new_height,
        "orig_size_mb": orig_size / 1024 / 1024,
        "new_size_mb": compressed_size / 1024 / 1024,
        "scale_ratio": scale_ratio,
        "compressed": True,
    }
    return buffer, meta


def prepare_upload_payload(file_path):
    """Return (files dict, cleanup callback, meta info)."""
    ext = os.path.splitext(file_path.lower())[1]
    is_image = ext in IMAGE_COMPRESS_EXTS

    if ENABLE_CLIENT_COMPRESS and is_image:
        buffer, meta = compress_image_for_upload(file_path)
        meta.update({
            "ext": ext,
            "is_pdf": False
        })
        files = {'file': (meta['filename'], buffer, meta['content_type'])}

        def _cleanup():
            buffer.close()

        return files, _cleanup, meta

    file_obj = open(file_path, 'rb')
    orig_size_mb = os.path.getsize(file_path) / 1024 / 1024
    content_type = "application/pdf" if ext == '.pdf' else "application/octet-stream"
    meta = {
        "filename": os.path.basename(file_path),
        "content_type": content_type,
        "orig_width": None,
        "orig_height": None,
        "new_width": None,
        "new_height": None,
        "orig_size_mb": orig_size_mb,
        "new_size_mb": orig_size_mb,
        "scale_ratio": 1.0,
        "compressed": False,
        "ext": ext,
        "is_pdf": ext == '.pdf'
    }
    files = {'file': (meta['filename'], file_obj, meta['content_type'])}

    def _cleanup():
        file_obj.close()

    return files, _cleanup, meta


def test_ocr_service():
    """测试OCR服务的API接口"""

    print(f"🔄 测试{OCR_ENGINE_NAME} OCR服务API... (服务器: {OCR_BASE_URL})")

    # 1. 测试服务状态
    try:
        response = requests.get(f"{OCR_BASE_URL}/", timeout=5)
        service_info = response.json()
        print("✅ 服务状态:", service_info)
        
        # 显示引擎信息
        if 'engine' in service_info:
            print(f"   引擎: {service_info['engine']}")
        if 'expected_performance' in service_info:
            print(f"   性能: {service_info['expected_performance']}")
            
    except requests.exceptions.ConnectionError:
        print(f"❌ {OCR_ENGINE_NAME}服务未启动！请先运行相应服务:")
        if "5000" in OCR_BASE_URL:
            print("   python run_ocr_service.py")
        else:
            print("   python run_easyocr_service.py") 
        return False
    except Exception as e:
        print(f"❌ 服务测试失败: {e}")
        return False

    # 2. 测试OCR识别
    test_file = DEFAULT_TEST_FILE
    try:
        files, cleanup, meta = prepare_upload_payload(test_file)
        if meta['compressed']:
            print(f"🗜️ 客户端压缩: {meta['orig_size_mb']:.2f}MB → {meta['new_size_mb']:.2f}MB")
            print(f"   分辨率: {meta['orig_width']}×{meta['orig_height']} → {meta['new_width']}×{meta['new_height']}")
        else:
            if meta.get('is_pdf'):
                print(f"🗜️ 客户端压缩: PDF暂不压缩 (上传原文件 {meta['orig_size_mb']:.2f}MB)")
            else:
                print(f"🗜️ 客户端压缩: 关闭 (上传原图 {meta['orig_size_mb']:.2f}MB)")

        # OCR 对大图/长文本耗时较长，拉长超时窗口
        start_ts = time.time()
        try:
            response = requests.post(f'{OCR_BASE_URL}/ocr', files=files, timeout=600)
        finally:
            cleanup()
        cost = time.time() - start_ts

        if response.status_code == 200:
            result = response.json()
            print(f"✅ {OCR_ENGINE_NAME} OCR测试成功!")
            print(f"   文件: {meta['filename']}")
            print(f"   大小: {meta['new_size_mb']:.2f} MB")
            print(f"   耗时: {cost:.2f} 秒")
            print(f"   识别到 {result.get('text_count', 0)} 段文本")
            
            # 显示引擎信息（如果有）
            if 'engine' in result:
                print(f"   引擎: {result['engine']}")

            if 'full_text' in result:
                print("\n📝 识别结果:")
                print("-" * 40)
                print(result['full_text'])
                print("-" * 40)

            pages = result.get('pages')
            if pages:
                print("\n📄 分页结果:")
                for page in pages:
                    print(f"   第{page.get('page')}页: {page.get('text_count', 0)} 段")
                    if page.get('texts'):
                        sample = page['texts'][0].get('text', '')
                        if sample:
                            preview = sample if len(sample) <= 60 else sample[:57] + '...'
                            print(f"      示例: {preview}")

            return True
        else:
            print(f"❌ {OCR_ENGINE_NAME} OCR请求失败: HTTP {response.status_code}")
            print(f"   错误信息: {response.text}")
            print(f"   耗时: {cost:.2f} 秒")
            print(f"   上传大小: {meta['new_size_mb']:.2f} MB")
            return False

    except FileNotFoundError:
        print(f"❌ 测试文件不存在: {test_file}")
        print("   请确保当前目录有 test.jpg 文件")
        return False
    except Exception as e:
        print(f"❌ OCR测试异常: {e}")
        return False

if __name__ == '__main__':
    print("="*60)
    print(f"📡 当前测试配置: {OCR_ENGINE_NAME} ({OCR_BASE_URL})")
    print("🔧 切换服务器方法: 修改文件顶部的注释")
    print("   - PaddleOCR: localhost:5000 (35秒左右)")  
    print("   - EasyOCR:   localhost:5001 (10-15秒)")
    print(f"🖼️ 测试文件: {DEFAULT_TEST_FILE} (可通过 BIDGUARD_TEST_FILE 修改)")
    print("="*60)
    
    success = test_ocr_service()
    exit(0 if success else 1)
