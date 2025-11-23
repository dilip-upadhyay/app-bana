export enum PropType {
  Text = 'text',
  Number = 'number',
  Boolean = 'boolean',
  Color = 'color',
  Select = 'select',
  Textarea = 'textarea',
  Icon = 'icon',
  Style = 'style', // Special type for CSS style string
  Array = 'array',
  Object = 'object'
}

export interface PropDefinition {
  name: string;
  label?: string;
  type: PropType;
  defaultValue?: any;
  options?: { label: string; value: string | number }[]; // For Select type
  placeholder?: string;
  description?: string;
  group?: 'content' | 'style' | 'layout' | 'advanced' | 'events';
  hidden?: boolean;
}

export interface ComponentDefinition {
  type: string;
  label: string;
  icon?: string; // Emoji or icon name
  description?: string;
  props: PropDefinition[];
  defaultProps?: Record<string, any>;
  allowChildren?: boolean;
}

const definitions = new Map<string, ComponentDefinition>();

export function registerComponentDefinition(def: ComponentDefinition) {
  definitions.set(def.type, def);
}

export function getComponentDefinition(type: string): ComponentDefinition | undefined {
  return definitions.get(type);
}

export function getAllComponentDefinitions(): ComponentDefinition[] {
  return Array.from(definitions.values());
}

// --- Register Core Definitions ---

// Common style props that might be useful for many components
const commonStyleProps: PropDefinition[] = [
  { name: 'className', label: 'CSS Classes', type: PropType.Text, group: 'style', placeholder: 'e.g. p-4 bg-blue-500' },
  { name: 'id', label: 'ID', type: PropType.Text, group: 'advanced' },
  { name: 'hidden', label: 'Hidden', type: PropType.Boolean, group: 'advanced' }
];

// Container
registerComponentDefinition({
  type: 'container',
  label: 'Container',
  icon: '📦',
  description: 'A layout container for other components',
  allowChildren: true,
  props: [
    {
      name: 'layout',
      label: 'Layout',
      type: PropType.Select,
      group: 'layout',
      defaultValue: 'block',
      options: [
        { label: 'Block', value: 'block' },
        { label: 'Flex', value: 'flex' },
        { label: 'Grid', value: 'grid' }
      ]
    },
    {
      name: 'direction',
      label: 'Direction',
      type: PropType.Select,
      group: 'layout',
      defaultValue: 'row',
      options: [
        { label: 'Row', value: 'row' },
        { label: 'Column', value: 'column' }
      ]
    },
    { name: 'gap', label: 'Gap', type: PropType.Text, group: 'layout', placeholder: 'e.g. 1rem' },
    { name: 'padding', label: 'Padding', type: PropType.Text, group: 'layout', placeholder: 'e.g. 1rem' },
    { name: 'alignItems', label: 'Align Items', type: PropType.Select, group: 'layout', options: [{label:'Start',value:'flex-start'},{label:'Center',value:'center'},{label:'End',value:'flex-end'},{label:'Stretch',value:'stretch'}] },
    { name: 'justifyContent', label: 'Justify Content', type: PropType.Select, group: 'layout', options: [{label:'Start',value:'flex-start'},{label:'Center',value:'center'},{label:'End',value:'flex-end'},{label:'Space Between',value:'space-between'}] },
    ...commonStyleProps
  ]
});

// Text
registerComponentDefinition({
  type: 'text',
  label: 'Text',
  icon: '📝',
  description: 'Simple text block',
  allowChildren: false,
  props: [
    { name: 'text', label: 'Content', type: PropType.Textarea, group: 'content', defaultValue: 'Text content' },
    {
      name: 'tag',
      label: 'HTML Tag',
      type: PropType.Select,
      group: 'advanced',
      defaultValue: 'p',
      options: [
        { label: 'Paragraph (p)', value: 'p' },
        { label: 'Span', value: 'span' },
        { label: 'Heading 1 (h1)', value: 'h1' },
        { label: 'Heading 2 (h2)', value: 'h2' },
        { label: 'Heading 3 (h3)', value: 'h3' },
        { label: 'Div', value: 'div' }
      ]
    },
    ...commonStyleProps
  ]
});

// Button
registerComponentDefinition({
  type: 'button',
  label: 'Button',
  icon: '🔘',
  description: 'Clickable button',
  allowChildren: false,
  props: [
    { name: 'text', label: 'Label', type: PropType.Text, group: 'content', defaultValue: 'Button' },
    {
      name: 'variant',
      label: 'Variant',
      type: PropType.Select,
      group: 'style',
      defaultValue: 'primary',
      options: [
        { label: 'Primary', value: 'primary' },
        { label: 'Secondary', value: 'secondary' },
        { label: 'Outline', value: 'outline' },
        { label: 'Ghost', value: 'ghost' },
        { label: 'Danger', value: 'danger' }
      ]
    },
    { name: 'disabled', label: 'Disabled', type: PropType.Boolean, group: 'content' },
    ...commonStyleProps
  ]
});

// Input
registerComponentDefinition({
  type: 'input',
  label: 'Input',
  icon: '⌨️',
  description: 'Text input field',
  allowChildren: false,
  props: [
    { name: 'label', label: 'Label', type: PropType.Text, group: 'content' },
    { name: 'placeholder', label: 'Placeholder', type: PropType.Text, group: 'content' },
    { name: 'value', label: 'Value', type: PropType.Text, group: 'content' },
    {
      name: 'type',
      label: 'Type',
      type: PropType.Select,
      group: 'content',
      defaultValue: 'text',
      options: [
        { label: 'Text', value: 'text' },
        { label: 'Email', value: 'email' },
        { label: 'Password', value: 'password' },
        { label: 'Number', value: 'number' },
        { label: 'Date', value: 'date' }
      ]
    },
    { name: 'required', label: 'Required', type: PropType.Boolean, group: 'content' },
    { name: 'disabled', label: 'Disabled', type: PropType.Boolean, group: 'content' },
    ...commonStyleProps
  ]
});

// Image
registerComponentDefinition({
  type: 'img',
  label: 'Image',
  icon: '🖼️',
  description: 'Image display',
  allowChildren: false,
  props: [
    { name: 'src', label: 'Source URL', type: PropType.Text, group: 'content', placeholder: 'https://...' },
    { name: 'alt', label: 'Alt Text', type: PropType.Text, group: 'content' },
    { name: 'width', label: 'Width', type: PropType.Text, group: 'style' },
    { name: 'height', label: 'Height', type: PropType.Text, group: 'style' },
    ...commonStyleProps
  ]
});

// Textarea
registerComponentDefinition({
  type: 'textarea',
  label: 'Textarea',
  icon: '📝',
  description: 'Multi-line text input',
  allowChildren: false,
  props: [
    { name: 'label', label: 'Label', type: PropType.Text, group: 'content' },
    { name: 'placeholder', label: 'Placeholder', type: PropType.Text, group: 'content' },
    { name: 'rows', label: 'Rows', type: PropType.Number, group: 'style', defaultValue: 3 },
    { name: 'required', label: 'Required', type: PropType.Boolean, group: 'content' },
    { name: 'disabled', label: 'Disabled', type: PropType.Boolean, group: 'content' },
    ...commonStyleProps
  ]
});

// Select
registerComponentDefinition({
  type: 'select',
  label: 'Select',
  icon: '🔽',
  description: 'Dropdown selection',
  allowChildren: false,
  props: [
    { name: 'label', label: 'Label', type: PropType.Text, group: 'content' },
    { name: 'options', label: 'Options (JSON/CSV)', type: PropType.Textarea, group: 'content', description: 'Comma-separated or JSON array' },
    { name: 'value', label: 'Selected Value', type: PropType.Text, group: 'content' },
    { name: 'required', label: 'Required', type: PropType.Boolean, group: 'content' },
    { name: 'disabled', label: 'Disabled', type: PropType.Boolean, group: 'content' },
    ...commonStyleProps
  ]
});

// Checkbox
registerComponentDefinition({
  type: 'checkbox',
  label: 'Checkbox',
  icon: '☑️',
  description: 'Boolean toggle',
  allowChildren: false,
  props: [
    { name: 'label', label: 'Label', type: PropType.Text, group: 'content' },
    { name: 'checked', label: 'Checked', type: PropType.Boolean, group: 'content' },
    { name: 'disabled', label: 'Disabled', type: PropType.Boolean, group: 'content' },
    ...commonStyleProps
  ]
});

// Radio Group
registerComponentDefinition({
  type: 'radio-group',
  label: 'Radio Group',
  icon: '🔘',
  description: 'Single selection group',
  allowChildren: false,
  props: [
    { name: 'label', label: 'Label', type: PropType.Text, group: 'content' },
    { name: 'name', label: 'Group Name', type: PropType.Text, group: 'content' },
    { name: 'options', label: 'Options', type: PropType.Textarea, group: 'content', description: 'JSON array of {label, value}' },
    { name: 'value', label: 'Selected Value', type: PropType.Text, group: 'content' },
    { name: 'direction', label: 'Direction', type: PropType.Select, group: 'style', options: [{label:'Vertical',value:'column'},{label:'Horizontal',value:'row'}] },
    ...commonStyleProps
  ]
});
