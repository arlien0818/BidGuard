# test_access_method.py
from paddleocr import PaddleOCR

# 初始化OCR
ocr = PaddleOCR(lang='ch')

# 识别测试图片
result = ocr.predict("test.jpg")

if result and len(result) > 0:
    ocr_result = result[0]

    print("=== 测试访问方式 ===")

    # 方法1：字典方式（应该能工作）
    print("1. 字典方式访问:")
    try:
        texts = ocr_result['rec_texts']
        scores = ocr_result['rec_scores']
        print(f"   texts: {texts}")
        print(f"   scores: {scores}")
    except Exception as e:
        print(f"   ❌ 失败: {e}")

    # 方法2：get方法
    print("\n2. get()方法访问:")
    try:
        texts = ocr_result.get('rec_texts')
        scores = ocr_result.get('rec_scores')
        print(f"   texts: {texts}")
        print(f"   scores: {scores}")
    except Exception as e:
        print(f"   ❌ 失败: {e}")

    # 方法3：查看类型和可用方法
    print(f"\n3. 对象类型: {type(ocr_result)}")
    print(f"   是否有getattr: {hasattr(ocr_result, '__getitem__')}")
    print(f"   是否有get: {hasattr(ocr_result, 'get')}")

    # 方法4：尝试所有可能的键
    print("\n4. 尝试常见键名:")
    possible_keys = ['rec_texts', 'texts', 'text', 'result', 'words', 'lines', 'rec_scores', 'scores']
    for key in possible_keys:
        try:
            if key in ocr_result:
                value = ocr_result[key]
                print(f"   '{key}' 存在: {type(value)}")
                if isinstance(value, list) and len(value) > 0:
                    print(f"     示例: {value[0]}")
        except:
            try:
                value = getattr(ocr_result, key, None)
                if value is not None:
                    print(f"   '{key}' 属性存在: {type(value)}")
            except:
                pass
