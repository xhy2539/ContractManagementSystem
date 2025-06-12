// TinyMCE编辑器配置
window.initTinyMCE = function(selector) {
    tinymce.init({
        selector: selector,
        plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code fullscreen insertdatetime media table code help wordcount',
        toolbar: 'undo redo | formatselect | bold italic backcolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | removeformat | help',
        height: 500,
        menubar: true,
        branding: false,
        language: 'zh_CN',
        // 配置图片上传
        images_upload_url: '/api/upload/image',
        images_upload_base_path: '/uploads',
        images_upload_credentials: true,
        // 添加自定义配置
        setup: function (editor) {
            editor.on('init', function () {
                console.log('TinyMCE initialized successfully');
            });
        },
        // 配置内容样式
        content_style: 'body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 14px; }',
    });
}; 