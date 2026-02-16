# run_easyocr_service.py - 高速OCR服务 (EasyOCR版本)
import sys
import tempfile
import traceback

def main():
    try:
        print("🚀 启动BidGuard EasyOCR服务... (预期速度提升2-3倍)")

        # 尝试导入和启动
        import os
        import time
        import threading
        from datetime import datetime
        from flask import Flask, request, jsonify
        import easyocr
        from PIL import Image
        import fitz

        print("✅ 模块导入成功")

        app = Flask(__name__)

        print("🔄 正在初始化EasyOCR引擎...")
        try:
            start_init = time.time()
            
            # 🚀 EasyOCR配置 - 通常比PaddleOCR快2-3倍
            reader = easyocr.Reader(
                ['ch_sim', 'en'],              # 中英文支持
                gpu=False,                     # CPU模式 (如果有GPU可以设为True)
                model_storage_directory=None,  # 使用默认模型目录
                download_enabled=True,         # 允许下载模型
                detector=True,                 # 启用文本检测
                recognizer=True,               # 启用文本识别
                verbose=False,                 # 关闭详细日志
                quantize=True                  # 🔥 启用量化加速
            )
            
            easyocr_predict_args = {
                "detail": 1,            # 返回坐标 + 文本 + 置信度
                "paragraph": False,     # 不自动合并段落
                "width_ths": 0.7,       # 控制水平合并阈值
                "height_ths": 0.7,      # 控制垂直合并阈值
            }

            init_time = time.time() - start_init
            print(f"✅ EasyOCR引擎初始化成功，耗时 {init_time:.2f} 秒")
        except Exception as e:
            print(f"❌ EasyOCR引擎初始化失败: {e}")
            print("💡 提示：需要先安装 EasyOCR: pip install easyocr")
            print(traceback.format_exc())
            return 1

        # 保存到应用上下文
        app.config['OCR_ENGINE'] = reader
        app.config['EASYOCR_ARGS'] = easyocr_predict_args
        app.config['OCR_LOCK'] = threading.Lock()  # EasyOCR 线程安全，但为了稳妥还是加锁

        def compress_image_for_ocr(image_path, max_size=1200, quality=80):
            """
            为EasyOCR优化的图片压缩 (EasyOCR对尺寸不如PaddleOCR敏感)
            """
            try:
                with Image.open(image_path) as img:
                    orig_width, orig_height = img.size
                    print(f"📏 原始图片尺寸: {orig_width}×{orig_height}")
                    
                    # EasyOCR可以处理稍大的图片，不需要过度压缩
                    max_dimension = max(orig_width, orig_height)
                    if max_dimension <= max_size:
                        print(f"✅ 图片尺寸适合EasyOCR，无需压缩")
                        return image_path
                    
                    # 适度压缩即可
                    scale_ratio = max_size / max_dimension
                    new_width = int(orig_width * scale_ratio)
                    new_height = int(orig_height * scale_ratio)
                    print(f"🔄 适度压缩到: {new_width}×{new_height} (比例: {scale_ratio:.3f})")
                    
                    # 压缩
                    compressed_img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
                    
                    # 保存
                    base_name = os.path.splitext(image_path)[0]
                    file_ext = os.path.splitext(image_path)[1]
                    compressed_path = f"{base_name}_easyocr{file_ext}"
                    
                    if file_ext.lower() in ['.jpg', '.jpeg']:
                        compressed_img.save(compressed_path, 'JPEG', quality=quality, optimize=True)
                    else:
                        compressed_img.save(compressed_path, optimize=True)
                    
                    # 🚨 DEBUG模式 (正式版删除)
                    debug_dir = "tmp"
                    if not os.path.exists(debug_dir):
                        os.makedirs(debug_dir)
                    
                    timestamp = datetime.now().strftime("%H%M%S")
                    debug_path = os.path.join(debug_dir, f"easyocr_{timestamp}{file_ext}")
                    compressed_img.save(debug_path)
                    print(f"🐛 DEBUG: EasyOCR压缩图保存到 {debug_path}")
                    # 🚨 DEBUG模式结束
                    
                    print(f"💾 文件大小: {os.path.getsize(image_path)/1024/1024:.1f}MB → {os.path.getsize(compressed_path)/1024/1024:.1f}MB")
                    
                    return compressed_path
                    
            except Exception as e:
                print(f"⚠️ 图片压缩失败，使用原图: {e}")
                return image_path

        def render_pdf_to_images(pdf_path, dpi=200):
            """将PDF每一页渲染为临时图片，返回(page_no, path)列表"""
            pages = []
            try:
                doc = fitz.open(pdf_path)
            except Exception as e:
                print(f"⚠️ PDF解析失败: {e}")
                raise

            try:
                zoom = dpi / 72.0
                mat = fitz.Matrix(zoom, zoom)
                for page_index in range(doc.page_count):
                    page = doc.load_page(page_index)
                    pix = page.get_pixmap(matrix=mat, alpha=False)
                    tmp_file = tempfile.NamedTemporaryFile(delete=False, suffix=".png")
                    tmp_file.close()
                    pix.save(tmp_file.name)
                    pages.append((page_index + 1, tmp_file.name))
                    print(f"📄 PDF转图: 第{page_index + 1}页 -> {tmp_file.name}")
            finally:
                doc.close()

            return pages

        @app.route('/')
        def home():
            return jsonify({
                "service": "BidGuard EasyOCR Service",
                "status": "running",
                "engine": "EasyOCR",
                "expected_performance": "2-3x faster than PaddleOCR",
                "time": datetime.now().isoformat()
            })

        @app.route('/ocr', methods=['POST'])
        def process_ocr():
            try:
                if 'file' not in request.files:
                    return jsonify({"error": "缺少文件"}), 400

                file = request.files['file']
                if file.filename == '':
                    return jsonify({"error": "空文件名"}), 400

                file_ext = os.path.splitext(file.filename.lower())[1]
                tmp_path = None
                transient_paths = set()
                page_results = []
                start_ts = time.time()
                try:
                    with tempfile.NamedTemporaryFile(delete=False, suffix=file_ext) as tmp:
                        file.save(tmp.name)
                        tmp_path = tmp.name

                    io_done_ts = time.time()
                    print(f"🖼️ 原始文件: {file.filename}")

                    page_entries = []
                    if file_ext == '.pdf':
                        page_entries = render_pdf_to_images(tmp_path, dpi=220)
                        if not page_entries:
                            return jsonify({"error": "PDF文件没有有效页面"}), 400
                        for _, path in page_entries:
                            transient_paths.add(path)
                        print(f"📚 发现 {len(page_entries)} 页 PDF，依次识别")
                    else:
                        page_entries = [(1, tmp_path)]

                    predict_args = app.config['EASYOCR_ARGS']
                    print("⚙️ EasyOCR参数:", ", ".join(f"{k}={v}" for k, v in predict_args.items()))

                    for page_number, page_path in page_entries:
                        print(f"🔁 EasyOCR 第{page_number}页压缩+识别")
                        page_pre_start = time.time()
                        compressed_path = compress_image_for_ocr(page_path, max_size=1200)
                        page_compress_done = time.time()

                        with app.config['OCR_LOCK']:
                            page_result = reader.readtext(
                                compressed_path,
                                **predict_args
                            )
                        page_pred_done = time.time()

                        page_results.append((page_number, page_result))
                        if compressed_path and compressed_path != page_path:
                            transient_paths.add(compressed_path)

                        print(f"⏱️ 第{page_number}页性能:")
                        print(f"   🖼️ 图片优化: {page_compress_done - page_pre_start:.2f}s")
                        print(f"   🧠 OCR推理: {page_pred_done - page_compress_done:.2f}s")

                    total_done_ts = time.time()
                    print(f"📊 EasyOCR总耗时: {total_done_ts - start_ts:.2f}s (共 {len(page_entries)} 页)")
                    print(f"   📁 上传+写盘: {io_done_ts - start_ts:.2f}s")

                finally:
                    if tmp_path and os.path.exists(tmp_path):
                        os.unlink(tmp_path)
                    for extra_path in transient_paths:
                        if extra_path and extra_path != tmp_path and os.path.exists(extra_path):
                            os.unlink(extra_path)

                if not page_results:
                    return jsonify({"error": "识别失败"}), 500

                aggregated_items = []
                pages_summary = []
                for page_number, page_result in sorted(page_results, key=lambda x: x[0]):
                    page_items = []
                    if page_result:
                        for item in page_result:
                            bbox, text, confidence = item
                            bbox_py = [[float(pt[0]), float(pt[1])] for pt in bbox]
                            entry = {
                                "text": text,
                                "confidence": float(confidence),
                                "bbox": bbox_py,
                                "page": page_number
                            }
                            page_items.append(entry)
                            aggregated_items.append(entry)

                    pages_summary.append({
                        "page": page_number,
                        "text_count": len(page_items),
                        "texts": page_items,
                        "full_text": "\n".join(item["text"] for item in page_items)
                    })

                if aggregated_items:
                    return jsonify({
                        "success": True,
                        "engine": "EasyOCR",
                        "text_count": len(aggregated_items),
                        "texts": aggregated_items,
                        "full_text": "\n".join(item["text"] for item in aggregated_items),
                        "pages": pages_summary,
                        "page_count": len(pages_summary)
                    })
                else:
                    return jsonify({"error": "识别失败"}), 500

            except Exception as e:
                return jsonify({"error": str(e)}), 500

        print("\n" + "="*60)
        print("✅ EasyOCR 服务初始化完成")
        print(f"📡 服务地址: http://localhost:5001")  # 不同端口避免冲突
        print("🚀 预期性能: 比PaddleOCR快2-3倍 (目标10-15秒)")
        print("="*60 + "\n")

        # 启动Flask服务
        app.run(
            host='0.0.0.0',
            port=5001,  # 不同端口
            debug=False,
            threaded=False  # EasyOCR虽然线程安全，但保守起见
        )

    except KeyboardInterrupt:
        print("\n👋 EasyOCR服务已停止")
        return 0
    except Exception as e:
        print(f"\n❌ 启动失败: {e}")
        print(traceback.format_exc())
        return 1

if __name__ == '__main__':
    sys.exit(main())