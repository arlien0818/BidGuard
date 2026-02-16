# run_ocr_service.py
import os
import sys
import tempfile
import traceback


SAVE_INTERMEDIATE_DEFAULT = False  # 默认使用内存模式，避免写入中间文件

def main():
    try:
        print("🚀 启动BidGuard OCR服务...")

        # 尝试导入和启动
        import time
        import threading
        import numpy as np
        from datetime import datetime
        from flask import Flask, request, jsonify
        from paddleocr import PaddleOCR
        from PIL import Image
        import fitz

        print("✅ 模块导入成功")

        # 设置环境变量
        os.environ['PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK'] = 'True'
        if 'OMP_NUM_THREADS' not in os.environ:
            cpu_cnt = os.cpu_count() or 4
            os.environ['OMP_NUM_THREADS'] = str(max(1, min(cpu_cnt, 8)))
        if 'MKL_NUM_THREADS' not in os.environ:
            os.environ['MKL_NUM_THREADS'] = os.environ['OMP_NUM_THREADS']
        print(f"⚙️ 线程数: OMP_NUM_THREADS={os.environ['OMP_NUM_THREADS']}, MKL_NUM_THREADS={os.environ['MKL_NUM_THREADS']}")

        env_override = os.getenv('BIDGUARD_SAVE_INTERMEDIATE')
        if env_override is None:
            save_intermediate = SAVE_INTERMEDIATE_DEFAULT
        else:
            save_intermediate = env_override.lower() in {'1', 'true', 'yes', 'on'}
        mode_label = '环境变量' if env_override is not None else '默认变量'
        print(f"💾 压缩文件写盘: {'开启' if save_intermediate else '关闭'} ({mode_label}={'BIDGUARD_SAVE_INTERMEDIATE' if env_override else 'SAVE_INTERMEDIATE_DEFAULT'})")

        app = Flask(__name__)

        print("🔄 正在初始化OCR引擎...")
        try:
            start_init = time.time()
            # 🚀 极限OCR配置 - 最大程度牺牲精度换取速度
            ocr = PaddleOCR(
                lang='ch',                          # 语言设置
                use_textline_orientation=False,     # ❌ 关掉方向分类
                
                # 🔥 极限检测优化 - 大幅降低精度换速度
                text_det_thresh=0.1,                # 🔥 极低检测阈值 (默认0.3→0.1)
                text_det_box_thresh=0.3,            # 🔥 极低框阈值 (默认0.6→0.3)
                
                # 💾 系统优化  
                enable_mkldnn=True,                 # MKL-DNN加速
                cpu_threads=2                       # 🔥 进一步限制线程数，避免竞争
            )
            init_time = time.time() - start_init
            print(f"✅ OCR引擎初始化成功，耗时 {init_time:.2f} 秒")
        except Exception as e:
            print(f"❌ OCR引擎初始化失败: {e}")
            print(traceback.format_exc())
            return 1

        # 保存到应用上下文
        app.config['OCR_ENGINE'] = ocr
        app.config['OCR_LOCK'] = threading.Lock()  # PaddleOCR 线程不安全，使用锁串行化

        def compress_image_for_ocr(image_path, max_size=2000, quality=85, persist=False):
            """
            压缩图片以优化OCR性能
            Args:
                image_path: 原始图片路径
                max_size: 长边最大像素数
                quality: JPEG质量(1-100)
            Returns:
                tuple(ocr_input, persisted_path)
            """
            try:
                with Image.open(image_path) as img:
                    # 获取原始尺寸
                    orig_width, orig_height = img.size
                    print(f"📏 原始图片尺寸: {orig_width}×{orig_height}")
                    
                    # 🚀 OCR极速优化：极小尺寸换取速度
                    if max(orig_width, orig_height) > max_size:
                        # 极限压缩：800像素应该是最小的实用尺寸
                        max_size = min(max_size, 800)  # 🔥 极限尺寸
                    
                    # 检查是否需要压缩
                    max_dimension = max(orig_width, orig_height)
                    if max_dimension <= max_size:
                        print(f"✅ 图片尺寸合适，无需压缩")
                        # 🔥 即使不压缩也做预处理优化
                        return optimize_image_for_ocr(img, image_path, quality, persist)
                    
                    # 计算压缩比例
                    scale_ratio = max_size / max_dimension
                    new_width = int(orig_width * scale_ratio)
                    new_height = int(orig_height * scale_ratio)
                    print(f"🔄 压缩到: {new_width}×{new_height} (比例: {scale_ratio:.3f})")
                    
                    # 压缩图片 (使用LANCZOS算法获得最佳质量)
                    compressed_img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
                    
                    # 应用OCR优化预处理
                    return optimize_image_for_ocr(compressed_img, image_path, quality, persist)
                    
            except Exception as e:
                print(f"⚠️ 图片优化失败，使用原图: {e}")
                if persist:
                    return image_path, image_path
                with Image.open(image_path) as fallback_img:
                    return np.array(fallback_img.convert('RGB')), None

        def optimize_image_for_ocr(img, original_path, quality=75, persist=False):
            """
            🔥 OCR简化优化处理 - 减少预处理开销
            """
            try:
                # 🎯 转为RGB模式（统一处理）
                if img.mode != 'RGB':
                    img = img.convert('RGB')
                
                # 🔥 简化版：只做最基本的预处理，跳过锐化和对比度调整
                # 去掉了锐化和对比度增强，减少处理时间  
                
                file_ext = os.path.splitext(original_path)[1]
                
                if persist:
                    base_name = os.path.splitext(original_path)[0]
                    optimized_path = f"{base_name}_fast{file_ext}"  # 改名为fast
                    if file_ext.lower() in ['.jpg', '.jpeg']:
                        img.save(optimized_path, 'JPEG', quality=quality, optimize=True)
                    else:
                        img.save(optimized_path, optimize=True)

                    # 🚨 DEBUG模式仅在持久化时保留，方便分析
                    debug_dir = "tmp"
                    if not os.path.exists(debug_dir):
                        os.makedirs(debug_dir)

                    from datetime import datetime
                    timestamp = datetime.now().strftime("%H%M%S")
                    debug_path = os.path.join(debug_dir, f"fast_{timestamp}{file_ext}")
                    img.save(debug_path)
                    print(f"🐛 DEBUG: 激进优化后图片已保存到 {debug_path} (仅持久化模式)")

                    file_size_orig = os.path.getsize(original_path) / 1024 / 1024  # MB
                    file_size_opt = os.path.getsize(optimized_path) / 1024 / 1024  # MB
                    print(f"💾 文件大小: {file_size_orig:.1f}MB → {file_size_opt:.1f}MB")

                    return optimized_path, optimized_path

                # 非持久化：直接返回内存中的数组，避免磁盘IO
                optimized_array = np.array(img)[:, :, ::-1]  # PIL(RGB) -> BGR
                print(f"💡 内存中处理图片，像素矩阵尺寸: {optimized_array.shape}")
                return optimized_array, None
                
            except Exception as e:
                print(f"⚠️ 图片预处理失败，使用原图: {e}")
                if persist:
                    return original_path, original_path
                with Image.open(original_path) as fallback_img:
                    return np.array(fallback_img.convert('RGB'))[:, :, ::-1], None

        def render_pdf_to_images(pdf_path, dpi=200):
            """将多页PDF渲染为临时图片文件，返回(page_no, path)列表"""
            pdf_pages = []
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
                    pdf_pages.append((page_index + 1, tmp_file.name))
                    print(f"📄 PDF转图: 第{page_index + 1}页 -> {tmp_file.name}")
            finally:
                doc.close()

            return pdf_pages

        @app.route('/')
        def home():
            return jsonify({
                "service": "BidGuard OCR Service",
                "status": "running",
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
                        print(f"📚 发现 {len(page_entries)} 页 PDF，逐页识别")
                    else:
                        page_entries = [(1, tmp_path)]

                    for page_number, page_path in page_entries:
                        print(f"🔁 处理第 {page_number} 页")
                        page_pre_start = time.time()
                        compressed_payload, persisted_path = compress_image_for_ocr(
                            page_path,
                            max_size=800,
                            persist=save_intermediate
                        )
                        page_compress_done = time.time()

                        with app.config['OCR_LOCK']:
                            page_result = ocr.predict(compressed_payload)
                        page_pred_done = time.time()

                        page_results.append((page_number, page_result))
                        if persisted_path:
                            transient_paths.add(persisted_path)

                        print(f"⏱️ 第{page_number}页性能:")
                        print(f"   🖼️ 图片优化: {page_compress_done - page_pre_start:.2f}s")
                        print(f"   🧠 OCR推理: {page_pred_done - page_compress_done:.2f}s")

                    total_done_ts = time.time()
                    print(f"📊 文件总耗时: {total_done_ts - start_ts:.2f}s (含 {len(page_entries)} 页)")
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
                    texts = []
                    scores = []
                    if page_result and len(page_result) > 0:
                        ocr_result = page_result[0]
                        texts = ocr_result.get('rec_texts', [])
                        scores = ocr_result.get('rec_scores', [])

                    page_items = []
                    for i, text in enumerate(texts):
                        confidence = float(scores[i]) if i < len(scores) else 0.0
                        item = {
                            "text": text,
                            "confidence": confidence,
                            "page": page_number
                        }
                        page_items.append(item)
                        aggregated_items.append(item)

                    pages_summary.append({
                        "page": page_number,
                        "text_count": len(page_items),
                        "texts": page_items,
                        "full_text": "\n".join(t["text"] for t in page_items)
                    })

                if aggregated_items:
                    return jsonify({
                        "success": True,
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

        print("\n" + "="*50)
        print("✅ 服务初始化完成")
        print(f"📡 服务地址: http://localhost:5000")
        print("="*50 + "\n")

        # 启动Flask服务
        app.run(
            host='0.0.0.0',
            port=5000,
            debug=False,
            threaded=False  # 避免多线程并发使用同一个 OCR 引擎
        )

    except KeyboardInterrupt:
        print("\n👋 服务已停止")
        return 0
    except Exception as e:
        print(f"\n❌ 启动失败: {e}")
        print(traceback.format_exc())
        return 1

if __name__ == '__main__':
    sys.exit(main())
