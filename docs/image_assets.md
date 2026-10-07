# 보스·아이템 이미지 저장

보스와 드랍 아이템 이미지는 Vite의 `frontend/public/assets/boss/`,
`frontend/public/assets/items/`에 파일로 저장한다. Vercel은 빌드 결과의
`/assets/boss/<file>` 및 `/assets/items/<file>` 경로로 배포한다.

이미지 등록 순서:

1. 사용 권한과 출처를 확인한 PNG 또는 WebP 파일을 해당 디렉터리에 추가
2. `frontend/src/components/common/CatalogImage.tsx`의 `LOCAL_ASSET_PATHS`에 공개 경로 추가
3. DB의 `boss_image` 또는 `item_image`에 동일한 공개 경로 입력
4. `pnpm build` 후 배포 URL에서 `Content-Type: image/*`와 실제 화면 렌더링 확인

파일이 없거나 로딩에 실패하면 아이콘으로 대체한다. HTTP 200만으로 성공 판정 불가:
Vercel SPA fallback은 없는 이미지 경로에도 `text/html`을 반환할 수 있다.
이미지 파일을 추가한 PR은 빌드와 미리보기의 보스 카드·드랍 모달을 함께 확인한다.

현재 버전에는 검증된 실물 이미지 파일이 없다. 기존 DB의 미등록 `/assets/` 경로는
호출하지 않으며, S3/R2 및 별도 업로드 설정도 사용하지 않는다.
