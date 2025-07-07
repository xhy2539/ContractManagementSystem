from django.contrib import admin
from django.urls import path, include, re_path # 确保这里包含了 re_path

from rest_framework import permissions
from drf_yasg.views import get_schema_view
from drf_yasg import openapi

# Swagger 文档的元信息
schema_view = get_schema_view(
   openapi.Info(
      title="智慧车站API", # API 文档的标题
      default_version='v1', # 版本号
      description="智慧车站智能监控与大数据分析平台API文档", # 描述
      terms_of_service="https://www.google.com/policies/terms/",
      contact=openapi.Contact(email="contact@yourproject.local"),
      license=openapi.License(name="BSD License"),
   ),
   public=True, # 是否公开，这里设为 True 表示任何人都可以访问
   permission_classes=(permissions.AllowAny,), # 允许所有权限访问文档
)

urlpatterns = [
    path('admin/', admin.site.urls), # Django 后台管理
    path('api/users/', include('users.urls')), # 引入 users 应用的 URLS，路径为 /api/users/

    # Swagger 文档的 URL 配置
    re_path(r'^swagger(?P<format>\.json|\.yaml)$', schema_view.without_ui(cache_timeout=0), name='schema-json'),
    path('swagger/', schema_view.with_ui('swagger', cache_timeout=0), name='schema-swagger-ui'), # 在浏览器中访问这个地址看到 Swagger UI
    path('redoc/', schema_view.with_ui('redoc', cache_timeout=0), name='schema-redoc'), # ReDoc 风格的文档
]