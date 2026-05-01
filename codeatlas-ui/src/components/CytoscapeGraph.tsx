import { useEffect, useRef } from 'react';
import cytoscape from 'cytoscape';

export type CytoscapeElement = {
  data: Record<string, string>;
};

export function CytoscapeGraph({ elements }: { elements: CytoscapeElement[] }) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!containerRef.current || elements.length === 0) {
      return;
    }
    const cy = cytoscape({
      container: containerRef.current,
      elements,
      layout: { name: 'breadthfirst', directed: true, padding: 18, spacingFactor: 1.15 },
      style: [
        {
          selector: 'node',
          style: {
            'background-color': '#2f6f73',
            color: '#182230',
            label: 'data(label)',
            'font-size': '10px',
            'text-wrap': 'wrap',
            'text-max-width': '110px',
            'text-valign': 'bottom',
            'text-margin-y': 6
          }
        },
        {
          selector: 'edge',
          style: {
            'curve-style': 'bezier',
            'line-color': '#8fb9b7',
            'target-arrow-color': '#8fb9b7',
            'target-arrow-shape': 'triangle',
            label: 'data(label)',
            'font-size': '9px',
            'text-background-color': '#ffffff',
            'text-background-opacity': 0.86
          }
        }
      ]
    });
    cy.fit(undefined, 18);
    return () => {
      cy.destroy();
    };
  }, [elements]);

  if (elements.length === 0) {
    return null;
  }
  return <div ref={containerRef} className="cytoscape-graph" aria-label="Cytoscape graph explorer" />;
}
