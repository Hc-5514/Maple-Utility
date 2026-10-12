const icons = (theme: 'light' | 'dark') => ({
  calendar: `/assets/hunting/${theme}-calendar.svg`,
  check: `/assets/hunting/${theme}-check.svg`,
  chevron: `/assets/hunting/${theme}-chevron-down.svg`,
  chevronAccent: `/assets/hunting/${theme}-down.svg`,
  clock: `/assets/hunting/${theme}-clock.svg`,
  info: `/assets/hunting/${theme}-info.svg`,
  left: `/assets/hunting/${theme}-left.svg`,
  pencil: `/assets/hunting/${theme}-pencil.svg`,
  plus: `/assets/hunting/${theme}-plus.svg`,
  right: `/assets/hunting/${theme}-right.svg`,
  trash: `/assets/hunting/${theme}-trash.svg`,
  meso: '/assets/hunting/meso.png',
  solErda: '/assets/hunting/sol-erda.png',
})

export const huntingAssets = { light: icons('light'), dark: icons('dark') }
