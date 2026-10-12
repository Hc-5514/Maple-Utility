export function formatEok(value: number, digits = 1): string {
  return String(Number((value / 100_000_000).toFixed(digits)))
}

export function formatDecimal(value: number, digits = 1): string {
  return String(Number(value.toFixed(digits)))
}
