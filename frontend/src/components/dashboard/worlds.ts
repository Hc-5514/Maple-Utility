import { dashboardAssets } from './assets'
const assets = dashboardAssets.light
export const worlds = [
  ['스카니아', assets.imgWorldIcon1],
  ['베라', assets.imgWorldIcon2],
  ['루나', assets.imgWorldIcon3],
  ['제니스', assets.imgWorldIcon4],
  ['크로아', assets.imgWorldIcon5],
  ['유니온', assets.imgWorldIcon6],
  ['엘리시움', assets.imgWorldIcon],
  ['이노시스', assets.imgWorldIcon7],
  ['레드', assets.imgWorldIcon8],
  ['오로라', assets.imgWorldIcon9],
  ['아케인', assets.imgWorldIcon10],
  ['노바', assets.imgWorldIcon11],
  ['에오스', assets.imgWorldIcon12],
  ['헬리오스', assets.imgWorldIcon13],
] as const
export const worldNames: string[] = worlds.map(([name]) => name)
