# Notion Android UI 패치

삼성 Galaxy용 Kotlin Morphe 번들입니다. **Fix Notion Bottom Safe Area**와 **Preserve Notion Typography**를 각각 선택할 수 있습니다. 두 패치는 기본 활성화됩니다.

- 분석한 입력은 Google Play의 `notion.id` **0.6.4318 (10318)** 입니다. Galaxy `SM-S948N`의 Android 17 / One UI 9에서 추출한 base, `arm64_v8a`, `ko`, `xxhdpi` split 조합을 검증했습니다. Android 앱의 최소 SDK는 32입니다.
- 번들에는 패치 코드와 런타임 확장만 들어 있습니다. 테스트 APK, 서명키, Notion의 웹 번들과 글꼴은 로컬 분석 파일입니다.

## 하단 여백

`notion.local.id.MainActivity`는 Notion의 Compose 화면과 페이지용 WebView를 호스팅합니다. 두 스크린샷의 검색·AI·새 페이지 컨트롤은 `FloatingToolbarMode`를 사용하는 **네이티브 Compose** 툴바입니다. AI 레이블이 접근성 트리에서 `android.widget.TextView`로 표시되지만 Compose가 제공하는 가상 노드입니다.

이 빌드는 `targetSdkVersion=36`이고, 화면 하단까지 그리는 edge-to-edge 창에서 `stateHidden|adjustResize`를 사용합니다. 앱의 `kta.m` 인셋 리스너와 초기 `jb.a(Window)`는 `systemBars`와 `displayCutout`의 최댓값을 창 상태에 기록합니다. 키보드는 별도의 IME 인셋과 애니메이션으로 추적합니다. 하단 툴바는 이 창 안에서 배치되며 `h1e.invoke`가 컨트롤에 4dp의 하단 패딩을 더합니다. 창의 내비게이션 바 하단 인셋이 0이 되면 4dp만 남습니다.

### 스크린샷과 기기 좌표

기준 화면은 1440 × 3120px, 논리 밀도 560dpi입니다. `180923_Notion.jpg`에서는 힌트가 숨겨져 있고 `181013_Notion.jpg`에서는 힌트가 표시됩니다. 원본 Notion에서 ADB로 측정한 세 컨트롤의 상하 좌표가 스크린샷과 일치했습니다.

| 원본 Galaxy 상태 | `navigationBars` 하단 | 가시성 무시 내비게이션 하단 | `systemGestures` / `mandatorySystemGestures` 하단 | 컨트롤 Y 범위 | 화면 끝까지 여백 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 힌트 표시 | 53px | 53px | 112px / 112px | 2885–3053px | 67px |
| 힌트 숨김 | 0px | 0px | 112px / 112px | 2938–3106px | 14px |

힌트를 숨기면 One UI가 내비게이션 소스 프레임을 비웁니다. `getInsetsIgnoringVisibility(navigationBars())`도 0이며, 112px 전체를 사용하면 기준보다 59px 더 올라갑니다. 패치는 `h1e.invoke`의 4dp 값에만 연결됩니다. 현재 하단 인셋이 0이고 하단 제스처 영역이 있을 때 가시성 무시 인셋을 우선 사용하고, 그 값도 0이면 기준 기기에서 측정한 **15dp**를 사용합니다. 보정은 제스처 영역 이내로 제한합니다. 내비게이션 하단이나 측면 인셋이 존재해 이미 공간이 확보됐거나 IME가 표시되면 4dp를 사용합니다.

창마다 `WindowInsets`와 해당 뷰의 밀도를 읽고, 기존 Compose 인셋 상태를 구독합니다. 4dp와 보정량을 각각 픽셀로 반올림해 분수 밀도에서도 원래 패딩을 보존합니다. 패치 코드는 윈도우 모드, 제스처 설정, WebView의 CSS를 변경하지 않습니다. [Android WindowInsets](https://developer.android.com/develop/ui/views/layout/insets)

## 글꼴

Notion의 네이티브 텍스트 스타일은 `res/font/roboto_flex_variable.ttf`를 사용하고 기존 Compose 코드는 `wght`를 400·500·600·700으로 설정합니다. Galaxy에서 영어·숫자 샘플은 번들 글꼴로 렌더링됐고 한글은 기본 시스템 fallback을 사용했습니다. 기본 sans-serif를 쓰는 Compose 경로와 네이티브 편집기의 `CJK-Regular`·`CJK-Fallback`도 삼성 FlipFont의 영향을 받았습니다.

| 변경한 메서드 | 적용 범위 |
| --- | --- |
| `qwk.o(Context, Resources, int, String)` | Notion의 `roboto_flex_variable`을 읽을 때 `Typeface.CustomFallbackBuilder`의 시스템 fallback을 지정합니다. 기존 폰트 리소스, 스타일 설정, 캐시와 이후의 `wght` 적용 경로를 사용합니다. |
| `h3g.a(String, …, int)` | Compose의 기본 또는 명시적 `sans-serif`를 같은 기본 시스템 계열로 해석합니다. 기존 굵기와 이탤릭 인수를 사용합니다. |
| `hpg.b(String)` | 네이티브 편집기에서 `CJK-Regular`와 `CJK-Fallback`에 해당하는 경우에만 기본 시스템 계열을 사용합니다. 수식용 KaTeX, 이모지 및 레지스트리 캐시는 기존 경로를 따릅니다. |

세 경로는 삼성 기기에서만 활성화됩니다. 사용한 `arial`은 테스트한 One UI가 기본 `sans-serif`에 매핑하는 시스템 별칭입니다. 기본 설정에서 선택되는 Roboto·One UI Sans KR 및 로케일별 fallback 체계를 그대로 얻는다는 점을 글리프 테스트로 확인했습니다. 패치는 텍스트 크기, 자간, 줄 높이와 일반 Android `TextView` 테마를 변경하지 않습니다.

WebView 번들에는 Inter의 400·500·600·700, Lyon Text, iA Writer Mono, KaTeX 및 일부 스크립트용 웹 폰트가 포함되어 있습니다. 앱 셸에는 시스템 글꼴과 Helvetica·Arial 등을 포함한 CSS 스택도 있습니다. 같은 Galaxy에서 `system-ui`, `sans-serif`, 앱의 시스템 폰트 스택을 각각 400·700으로 테스트한 24개 샘플은 삼성 기본과 FlipFont에서 캔버스 픽셀 해시와 글자 폭이 일치했습니다. WebView의 CSS와 폰트 로딩에는 변경을 가하지 않았습니다.

두 패치는 Morphe Patcher 2.4.0의 `patch` DSL을 사용하는 바이트코드 패치입니다. fingerprint는 Compose enum과 그룹 키, 문자열, Android 메서드 호출 및 리소스 이름을 기준으로 대상을 찾습니다. 표에 적은 난독화 이름은 이 APK에서 발견한 분석 결과입니다. 런타임에서 패키지와 빌드 번호를 확인하므로 업데이트 APK에는 다시 분석과 검증이 필요합니다. [Morphe 공식 템플릿](https://github.com/MorpheApp/morphe-patches-template) · [Patcher 2.4.0](https://github.com/MorpheApp/morphe-patcher/tree/v2.4.0)

## 빌드 및 패치

Windows PowerShell 기준입니다. JDK 21 이상과 Android SDK Platform 36, Build-Tools 37.0.0을 준비하고 `JAVA_HOME`과 `ANDROID_HOME`을 설정합니다. 실제 빌드에는 JDK 25와 Gradle Wrapper 9.9.1을 사용했습니다. 컴파일에는 공개 Morphe Desktop 릴리스에 포함된 Patcher API를 사용합니다.

```powershell
$toolDir = New-Item -ItemType Directory -Force .local/tools
$desktop = Join-Path $toolDir.FullName 'morphe-desktop.jar'
$url = 'https://github.com/MorpheApp/morphe-desktop/releases/download/v2.2.0/morphe-desktop-2.2.0-all.jar'
curl.exe --fail --location $url --output $desktop
if ($LASTEXITCODE -ne 0) { throw 'Morphe Desktop download failed' }
$sha256 = '5ca0320ee05481df1ae3afd2f003bba3ff994d11d1f66ae48811ced71355e4fe'
if ((Get-FileHash $desktop).Hash -ne $sha256) { throw 'Unexpected Desktop JAR checksum' }

.\gradlew.bat buildAndroid
if ($LASTEXITCODE -ne 0) { throw 'Morphe bundle build failed' }
$bundle = 'build/libs/notion-patches.mpp'
& "$env:JAVA_HOME/bin/java.exe" -jar $desktop list-patches --patches $bundle --with-packages --with-versions
```

미리 빌드한 `notion-patches.mpp`가 있다면 `$bundle`에 그 경로를 지정할 수 있습니다. Morphe Manager를 사용할 경우 Patcher 2.4.0을 지원하는 버전에서 **Settings → Patch sources → + → Local**로 등록하고 Expert mode에서 두 패치를 선택합니다. 아래 Desktop 흐름을 실제로 검증했습니다. [Morphe Desktop 2.2.0](https://github.com/MorpheApp/morphe-desktop/releases/tag/v2.2.0) · [Morphe Manager 로컬 소스](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md)

Play 스토어 설치에서 추출한 base와 split을 한 `.apks` 파일에 담습니다. 기기가 여러 대면 추출할 Galaxy의 ADB 시리얼을 명시합니다.

```powershell
adb devices -l
$sourceDevice = '<Galaxy ADB serial>'
$source = New-Item -ItemType Directory -Force .local/input-0.6.4318
$apkPaths = adb -s $sourceDevice shell pm path notion.id
if ($LASTEXITCODE -ne 0 -or !$apkPaths) { throw 'Notion APK paths not available' }
foreach ($entry in $apkPaths) {
    adb -s $sourceDevice pull ($entry.Trim() -replace '^package:', '') $source.FullName
    if ($LASTEXITCODE -ne 0) { throw "Failed to pull $entry" }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$inputApks = Join-Path $source.Parent.FullName 'notion-0.6.4318.apks'
[IO.Compression.ZipFile]::CreateFromDirectory($source.FullName, $inputApks)

$output = '.local/notion-patched.apk'
& "$env:JAVA_HOME/bin/java.exe" -Xmx5g -jar $desktop patch --validate --bytecode-mode FULL `
    --patches $bundle --out $output --result-file .local/patch-result.json $inputApks
if ($LASTEXITCODE -ne 0) { throw 'Notion patching failed' }

$signer = Join-Path $env:ANDROID_HOME 'build-tools/37.0.0/apksigner.bat'
& $signer verify --print-certs (Join-Path $source.FullName 'base.apk')
& $signer verify --print-certs $output
```

하나만 테스트할 때는 `--patches $bundle` 뒤에 `--exclusive --enable 'Fix Notion Bottom Safe Area'` 또는 `--exclusive --enable 'Preserve Notion Typography'`를 추가합니다. 각 실행에 다른 `--out` 파일을 지정합니다.

### 설치 제약

검증한 출력의 Morphe 로컬 서명은 Play 스토어 APK의 서명과 달랐습니다. 같은 패키지의 업데이트는 호환되는 서명이 필요하므로 현재 Galaxy의 설치 위에 올릴 수 없습니다. 별도 기기나 Notion이 설치되지 않은 에뮬레이터를 선택합니다. [Android 앱 서명](https://developer.android.com/studio/publish/app-signing)

```powershell
$testDevice = '<ADB serial of empty test device>'
if ((adb -s $testDevice get-state) -ne 'device') { throw 'Test device unavailable' }
if ((adb -s $testDevice shell pm path notion.id) -match '^package:') {
    throw 'Notion is already installed on the selected device'
}
adb -s $testDevice install $output
if ($LASTEXITCODE -ne 0) { throw 'Installation failed' }
adb -s $testDevice shell am start -W -n notion.id/notion.local.id.MainActivity
```

Galaxy 원본의 `app.notion.com`과 `www.notion.so`는 Android App Links 검증에 통과했습니다. Morphe로 서명한 두 에뮬레이터 설치에서는 두 도메인의 자동 검증이 통과하지 않았습니다. 재서명은 인증서에 연결된 로그인 방식과 업데이트에도 영향을 줄 수 있습니다. 인증·서버 통신·결제·보안 검증 코드에는 변경을 적용하지 않았습니다. [Android App Links 검증](https://developer.android.com/training/app-links/verify-applinks)

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| 빌드 및 APK 적용 | `notion-patches.mpp`가 생성됐고 Desktop에서 패치 두 개를 확인했습니다. `--validate`로 하단 단독, 글꼴 단독, 두 패치 동시 적용과 서명을 완료했습니다. 세 APK에서 해당 DEX 호출을 각각 확인했고, 모듈 병합 직후 원본과 비교한 매니페스트, 리소스 테이블, 폰트, 웹 자산, 네이티브 라이브러리 등 7,802개 항목의 SHA-256이 일치했습니다. |
| SDK 정적 검사 | `dexdump`와 D8의 개별 DEX 검사와 `aapt2`·`apksigner`·`zipalign` 검사를 통과했습니다. 통합 `--verify-with-sdk`는 원본 APK의 외부 SDK에 대한 미해결 클래스 참조를 보고했습니다. 병합 원본과 동시 적용본을 동일한 검사기로 비교한 결과 중복을 제외한 139개 진단 항목이 일치했습니다. |
| 하단 패치 단독 | Android 17 에뮬레이터에서 설치 후 Notion 시작 화면까지 표시됐습니다. Galaxy 임시 진단 앱에서 네 가지 조합을 모두 측정했고, 힌트 표시 시 14px의 기존 로컬 패딩, 숨김 시 67px의 보정된 로컬 패딩을 확인했습니다. 기준 원본에서는 표시 시 53px의 시스템 인셋이 추가되어 둘 다 총 67px에 해당합니다. |
| 글꼴 패치 단독 | 별도 Android 17 에뮬레이터에서 설치 후 Notion 시작 화면까지 표시됐습니다. Galaxy 진단 앱에서 Notion APK의 Roboto Flex를 직접 사용해 36개 네이티브 샘플의 글꼴 파일, 글리프 ID, 위치와 폭을 비교했습니다. 삼성 기본값 기준과 네 조합에서 패치 결과가 모두 일치했습니다. 원본은 FlipFont에서 Roboto Flex 8/16, Compose 시스템 글꼴 16/16, 편집기 fallback 4/4가 달라졌습니다. |
| 키보드 | 제스처 힌트가 표시된 Galaxy 진단 앱에서 IME 표시 전 0px에서 표시 후 1322px을 관측했습니다. 해당 창에서 패치의 로컬 패딩은 14px로 유지됐습니다. |
| 화면 구성과 로그인 진입 | 에뮬레이터의 시작 화면을 다크 모드·가로 회전·화면 크기 변경 후와 라이트 모드·글꼴 배율 1.3에서 확인했습니다. 두 단독 설치 모두 기존 계정 버튼으로 브라우저를 열었습니다. |

기존 Notion이 있는 Galaxy에는 패치 APK를 설치하지 않았습니다. 실제 Notion 홈 스크린샷은 원본 두 상태까지 비교했고, 패치된 홈을 로그인한 Galaxy에서 다시 캡처하는 비교는 남아 있습니다. 화면 구성 변경은 에뮬레이터 시작 화면에서 확인했습니다. 실계정 로그인, 메모 검색과 AI 입력, 페이지 편집·스크롤, 실시간 동기화, 오프라인 데이터, 첨부와 푸시 수신은 종단간 검증 대상입니다. 다른 One UI 버전이나 WebView에서는 시스템 글꼴 별칭과 인셋을 다시 확인해야 합니다.

배포용 `notion-patches.mpp` SHA-256은 `38bbf0f3acf8ab970dd1c4266f26dc8483c00328910dbbcc13ecb7e2e1b5ae40`입니다.
