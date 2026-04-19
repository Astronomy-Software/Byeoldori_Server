import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: (
        <>
          <span>⭐</span>
          <span className="font-semibold">별도리 Dev Docs</span>
        </>
      ),
    },
  };
}
