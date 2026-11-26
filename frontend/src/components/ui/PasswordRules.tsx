import { PASSWORD_MAX_LENGTH, PASSWORD_RULES, checkPasswordRules, isPasswordTooLong } from "@/lib/auth/passwordRule";

import { Icon } from "./Icon";

/**
 * 비밀번호를 입력하는 동안 규칙 충족 여부를 체크로 보여주는 목록.
 * 규칙은 lib/auth/passwordRule.ts 한 곳에서 관리한다 (백엔드 PasswordRule과 동일).
 * 색만으로 구분하지 않도록 스크린 리더용 "충족/미충족" 글자를 함께 둔다.
 */
export function PasswordRules({ value, id }: { value: string; id?: string }) {
  const result = checkPasswordRules(value);
  const tooLong = isPasswordTooLong(value);

  return (
    <div id={id} className="flex flex-col gap-1">
      <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label="비밀번호 규칙">
        {PASSWORD_RULES.map((rule) => {
          const ok = result[rule.id];
          return (
            <li
              key={rule.id}
              className={`flex items-center gap-1 text-xs transition-colors ${ok ? "font-medium text-success" : "text-muted"}`}
            >
              <Icon name="tick" size={14} strokeWidth={2.4} />
              {rule.label}
              <span className="sr-only">{ok ? "충족" : "미충족"}</span>
            </li>
          );
        })}
      </ul>
      {tooLong && <p className="text-xs text-danger">비밀번호는 {PASSWORD_MAX_LENGTH}자 이하로 입력해 주세요.</p>}
    </div>
  );
}
