#!/usr/bin/env node
/**
 * PostToolUse hook: docs/requirements.md 가 Write/Edit 로 수정되면
 * Claude 에게 CLAUDE.md 를 요구사항에 맞춰 재작성(동기화)하라는 지시를 주입한다.
 *
 * 표준입력으로 hook 이벤트 JSON 을 받고, 수정된 파일이 requirements.md 일 때만
 * hookSpecificOutput.additionalContext 를 출력한다. 그 외에는 아무것도 출력하지 않는다.
 */
let input = "";
process.stdin.on("data", (d) => (input += d));
process.stdin.on("end", () => {
  let file = "";
  try {
    const j = JSON.parse(input || "{}");
    file =
      (j.tool_input && j.tool_input.file_path) ||
      (j.tool_response && j.tool_response.filePath) ||
      "";
  } catch (e) {
    process.exit(0);
  }

  const normalized = String(file).replace(/\\/g, "/");
  if (!/docs\/requirements\.md$/i.test(normalized)) {
    process.exit(0); // 관심 대상 파일이 아니면 조용히 종료
  }

  const instruction = [
    "docs/requirements.md 가 방금 수정되었습니다.",
    "변경된 요구사항에 맞춰 프로젝트 루트의 CLAUDE.md 를 재작성(동기화)하세요.",
    "규칙:",
    "1) '이 환경은 Server 만 개발한다'는 최상위 원칙(0장)은 반드시 유지한다.",
    "2) CLAUDE.md 의 기존 섹션 구조(0.개발범위 / 1.기술스택 / 2.기능 / 3.인터페이스규격 / 4.오류처리 / 5.공통원칙 / 6.미결정이슈 / 7.참조문서)를 유지하되, requirements.md 의 최신 내용과 어긋나는 항목은 갱신한다.",
    "3) requirements.md 에서 확정된 이슈는 6장 미결정 이슈에서 제거하고 해당 섹션에 반영한다.",
    "4) Server 와 무관한 Client 전용 세부사항은 CLAUDE.md 에 옮기지 않는다(인터페이스 계약은 예외).",
    "",
    "또한 setup 에이전트와 설치 스크립트도 최신 요구사항에 맞게 재정의하세요:",
    "5) '.claude/skills/server-setup/tools.manifest.json' 을 requirements.md 의 최신 Server 도구/버전/IntelliJ 플러그인 목록에 맞게 재생성한다(Client 전용 도구 제외).",
    "6) manifest 변경으로 영향받는 '.claude/skills/server-setup/scripts/' 의 단계 스크립트(01~07)와 SKILL.md 를 갱신한다. 추가된 도구는 새 단계나 기존 단계에 반영하고, 제거된 도구의 설치 로직은 삭제한다.",
    "7) '.claude/agents/server-setup.md' 의 설명/절차가 변경된 도구 구성과 일치하도록 갱신한다.",
    "8) 인터페이스 계약 값(RTSP 포트/경로, 코덱 등)이 requirements.md 에서 바뀌었다면 mediamtx.yml 생성 로직(05 단계)과 manifest 의 interfaceContract 에도 반영한다.",
    "동기화가 끝나면 CLAUDE.md/manifest/스크립트/에이전트 중 무엇을 바꿨는지 한두 줄로 요약해 사용자에게 보고하세요.",
  ].join(" ");

  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PostToolUse",
        additionalContext: instruction,
      },
    })
  );
  process.exit(0);
});
