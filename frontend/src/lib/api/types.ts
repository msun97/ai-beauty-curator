/** 백엔드 ResAuthUserDTO */
export type AuthUser = {
  id: number;
  email: string;
  nickname: string;
  role: "USER" | "ADMIN";
};
