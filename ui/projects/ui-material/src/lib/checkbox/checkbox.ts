import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCheckboxModule } from '@angular/material/checkbox';

@Component({
  selector: 'ab-checkbox',
  standalone: true,
  imports: [CommonModule, MatCheckboxModule],
  templateUrl: './checkbox.html',
  styleUrl: './checkbox.css'
})
export class Checkbox {
  @Input() label: string = '';
  @Input() disabled: boolean = false;
  @Input() checked: boolean = false;
  @Input() indeterminate: boolean = false;
}
