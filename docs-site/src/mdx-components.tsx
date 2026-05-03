import defaultComponents from 'fumadocs-ui/mdx';
import { APIPage } from '@/lib/openapi';
import type { MDXComponents } from 'mdx/types';

export function getMDXComponents(components?: MDXComponents): MDXComponents {
  return { ...defaultComponents, APIPage, ...components };
}

export function useMDXComponents(components: MDXComponents): MDXComponents {
  return getMDXComponents(components);
}
